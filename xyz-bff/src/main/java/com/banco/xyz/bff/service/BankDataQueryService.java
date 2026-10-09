package com.banco.xyz.bff.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class BankDataQueryService {

	private final BackendClient backend;

	public BankDataQueryService(BackendClient backend) {
		this.backend = backend;
	}

	// ============================================================
	// WEB - RESUMEN GLOBAL
	// ============================================================

	public Summary summary() {

		List<WebTransaction> transacciones = transactions();
		List<Account> cuentas = accounts();

		int totalTransacciones = transacciones.size();

		int transaccionesAnomalas = (int) transacciones.stream()
				.filter(WebTransaction::anomalia)
				.count();

		int cuentasConInteres = cuentas.size();

		int movimientosAnuales = cuentas.stream()
				.mapToInt(cuenta -> yearlyMovementsById(cuenta.id()).size())
				.sum();

		return new Summary(
				totalTransacciones,
				transaccionesAnomalas,
				cuentasConInteres,
				movimientosAnuales);
	}

	// ============================================================
	// WEB - TRANSACCIONES
	// ============================================================

	public List<WebTransaction> transactions() {

		WebTransactionResponse[] response = backend
				.get("web", "/transacciones/diarias")
				.retrieve()
				.body(WebTransactionResponse[].class);

		if (response == null) {
			return List.of();
		}

		return Arrays.stream(response)
				.map(tx -> new WebTransaction(
						tx.id(),
						LocalDate.parse(tx.fecha()),
						tx.monto(),
						tx.tipo(),
						tx.anomalia(),
						tx.observacion()))
				.toList();
	}

	// ============================================================
	// MOBILE - TRANSACCIONES RECIENTES
	// ============================================================

	public List<MobileTransaction> recentTransactions(int limit) {

		if (limit < 1 || limit > 100) {
			throw new IllegalArgumentException(
					"limit debe estar entre 1 y 100");
		}

		WebTransactionResponse[] response = backend
				.get(
						"mobile",
						"/transacciones/diarias?limit=" + limit)
				.retrieve()
				.body(WebTransactionResponse[].class);

		if (response == null) {
			return List.of();
		}

		return Arrays.stream(response)
				.map(tx -> new MobileTransaction(
						tx.id(),
						LocalDate.parse(tx.fecha()),
						tx.monto(),
						tx.tipo()))
				.toList();
	}

	// ============================================================
	// CUENTAS
	// ============================================================

	public List<Account> accounts() {

		AccountResponse[] response = backend
				.get("web", "/intereses/cuentas")
				.retrieve()
				.body(AccountResponse[].class);

		if (response == null) {
			return List.of();
		}

		return Arrays.stream(response)
				.map(this::toAccount)
				.toList();
	}

	// ============================================================
	// CUENTA POR ID
	// ============================================================

	public Optional<Account> accountById(long id) {

		try {

			AccountResponse response = backend
					.get("mobile", "/intereses/cuentas/" + id)
					.retrieve()
					.body(AccountResponse.class);

			if (response == null) {
				return Optional.empty();
			}

			return Optional.of(toAccount(response));

		} catch (HttpClientErrorException.NotFound ex) {
			return Optional.empty();
		}
	}

	// ============================================================
	// ESTADO ANUAL
	//
	// Mantiene la firma original del servicio.
	// Recibe cuentaId de negocio, por lo que primero buscamos
	// el id interno correspondiente.
	// ============================================================

	public List<Movement> yearlyMovements(long cuentaId) {

		Optional<Account> account = accounts().stream()
				.filter(cuenta ->
						Long.toString(cuentaId)
								.equals(cuenta.cuentaId()))
				.findFirst();

		if (account.isEmpty()) {
			return List.of();
		}

		return yearlyMovementsById(account.get().id());
	}

	// ============================================================
	// ATM - RETIRO
	// ============================================================

	public Withdrawal withdraw(long id, BigDecimal amount) {

		if (amount == null || amount.signum() <= 0) {
			throw new IllegalArgumentException(
					"Monto de retiro debe ser positivo");
		}

		/*
		 * Consultamos la cuenta antes del retiro.
		 * Esto permite mantener la validación del BFF ATM.
		 */
		Account account = accountByIdAtm(id)
				.orElseThrow(() ->
						new IllegalArgumentException(
								"Cuenta no encontrada"));

		if (account.saldoFinal().compareTo(amount) < 0) {
			throw new IllegalStateException(
					"Saldo insuficiente");
		}

		/*
		 * IMPORTANTE:
		 *
		 * El BFF ya NO actualiza la BD.
		 *
		 * Envía la solicitud a ms-transacciones.
		 * Desde allí comienza la Saga JMS con ms-cuentas.
		 */
		RetiroRequest request =
				new RetiroRequest(
						id,
						amount,
						false,
						false);

		RetiroResponse response = backend
				.post("atm", "/transacciones/retiros")
				.body(request)
				.retrieve()
				.body(RetiroResponse.class);

		if (response == null) {
			throw new IllegalStateException(
					"No se recibió respuesta del servicio de retiros");
		}

		return new Withdrawal(
				response.id(),
				response.eventId(),
				response.cuentaId(),
				response.monto(),
				response.estado(),
				response.detalle());
	}

	// ============================================================
	// MÉTODOS INTERNOS
	// ============================================================

	private Optional<Account> accountByIdAtm(long id) {

		try {

			AccountResponse response = backend
					.get("atm", "/cuentas/" + id)
					.retrieve()
					.body(AccountResponse.class);

			if (response == null) {
				return Optional.empty();
			}

			return Optional.of(toAccount(response));

		} catch (HttpClientErrorException.NotFound ex) {
			return Optional.empty();
		}
	}

	private List<Movement> yearlyMovementsById(long id) {

		try {

			MovementResponse[] response = backend
					.get(
							"web",
							"/cuentas/" + id + "/estado-anual")
					.retrieve()
					.body(MovementResponse[].class);

			if (response == null) {
				return List.of();
			}

			return Arrays.stream(response)
					.map(movement ->
							new Movement(
									LocalDate.parse(
											movement.fecha()),
									movement.transaccion(),
									movement.monto(),
									movement.descripcion(),
									movement.clasificacion()))
					.toList();

		} catch (HttpClientErrorException.NotFound ex) {
			return List.of();
		}
	}

	private Account toAccount(AccountResponse response) {

		return new Account(
				response.id(),
				response.cuentaId(),
				response.nombre(),
				response.saldoInicial(),
				response.edad(),
				response.tipo(),
				response.tasaInteres(),
				response.interesCalculado(),
				response.saldoFinal());
	}

	// ============================================================
	// DTOs PÚBLICOS DEL BFF
	// ============================================================

	public record Summary(
			int totalTransacciones,
			int transaccionesAnomalas,
			int cuentasConInteres,
			int movimientosAnuales) {
	}

	public record WebTransaction(
			long id,
			LocalDate fecha,
			BigDecimal monto,
			String tipo,
			boolean anomalia,
			String observacion) {
	}

	public record MobileTransaction(
			long id,
			LocalDate fecha,
			BigDecimal monto,
			String tipo) {
	}

	public record Account(
			long id,
			String cuentaId,
			String nombre,
			BigDecimal saldoInicial,
			int edad,
			String tipo,
			BigDecimal tasaInteres,
			BigDecimal interesCalculado,
			BigDecimal saldoFinal) {
	}

	public record Movement(
			LocalDate fecha,
			String transaccion,
			BigDecimal monto,
			String descripcion,
			String clasificacion) {
	}

	/*
	 * Ahora Withdrawal representa el resultado de la solicitud
	 * asíncrona procesada por ms-transacciones.
	 */
	public record Withdrawal(
			long id,
			String eventId,
			long cuentaId,
			BigDecimal montoRetirado,
			String estado,
			String detalle) {
	}

	// ============================================================
	// DTOs PARA RESPUESTAS DE LOS MICROSERVICIOS
	// ============================================================

	private record WebTransactionResponse(
			long id,
			String fecha,
			BigDecimal monto,
			String tipo,
			boolean anomalia,
			String observacion) {
	}

	private record AccountResponse(
			long id,
			String cuentaId,
			String nombre,
			BigDecimal saldoInicial,
			int edad,
			String tipo,
			BigDecimal tasaInteres,
			BigDecimal interesCalculado,
			BigDecimal saldoFinal) {
	}

	private record MovementResponse(
			long id,
			long cuentaId,
			String fecha,
			String transaccion,
			BigDecimal monto,
			String descripcion,
			String clasificacion) {
	}

	private record RetiroRequest(
			Long cuentaId,
			BigDecimal monto,
			Boolean simularFallo,
			Boolean simularDuplicado) {
	}

	private record RetiroResponse(
			long id,
			String eventId,
			long cuentaId,
			BigDecimal monto,
			String estado,
			String detalle) {
	}
}