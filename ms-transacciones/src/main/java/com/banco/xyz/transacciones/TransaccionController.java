package com.banco.xyz.transacciones;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TransaccionController {

	private final RetiroService retiros;
	private final JdbcTemplate jdbc;

	public TransaccionController(
			RetiroService retiros,
			JdbcTemplate jdbc) {

		this.retiros = retiros;
		this.jdbc = jdbc;
	}

	/*
	 * ============================================================
	 * RETIROS / SAGA JMS
	 * ============================================================
	 */

	@GetMapping("/transacciones")
	public Object listar() {
		return retiros.listar();
	}

	@PostMapping("/transacciones/retiros")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public RetiroService.RetiroView crear(
			@RequestBody RetiroRequest request) {

		if (request.cuentaId() == null
				|| request.monto() == null
				|| request.monto().signum() <= 0) {

			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"cuentaId y monto positivo son obligatorios");
		}

		return retiros.solicitar(
				request.cuentaId(),
				request.monto(),
				Boolean.TRUE.equals(request.simularFallo()),
				Boolean.TRUE.equals(request.simularDuplicado()));
	}

	@GetMapping("/transacciones/retiros/{id}")
	public RetiroService.RetiroView ver(@PathVariable long id) {
		return retiros.buscar(id);
	}

	/*
	 * ============================================================
	 * TRANSACCIONES DIARIAS PROCESADAS POR SPRING BATCH
	 * ============================================================
	 */

	@GetMapping("/transacciones/diarias")
	public List<TransaccionDiariaView> transaccionesDiarias(
			@RequestParam(required = false) Integer limit) {

		if (limit != null && (limit < 1 || limit > 100)) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"limit debe estar entre 1 y 100");
		}

		if (limit == null) {

			return jdbc.query("""
					SELECT id,
					       fecha,
					       monto,
					       tipo,
					       anomalia,
					       observacion
					FROM transacciones_diarias
					ORDER BY fecha DESC, id DESC
					""",
					(rs, row) -> new TransaccionDiariaView(
							rs.getLong("id"),
							rs.getDate("fecha").toLocalDate().toString(),
							rs.getBigDecimal("monto"),
							rs.getString("tipo"),
							rs.getBoolean("anomalia"),
							rs.getString("observacion")));
		}

		return jdbc.query("""
				SELECT id,
				       fecha,
				       monto,
				       tipo,
				       anomalia,
				       observacion
				FROM transacciones_diarias
				ORDER BY fecha DESC, id DESC
				LIMIT ?
				""",
				(rs, row) -> new TransaccionDiariaView(
						rs.getLong("id"),
						rs.getDate("fecha").toLocalDate().toString(),
						rs.getBigDecimal("monto"),
						rs.getString("tipo"),
						rs.getBoolean("anomalia"),
						rs.getString("observacion")),
				limit);
	}

	/*
	 * ============================================================
	 * DTOs
	 * ============================================================
	 */

	public record RetiroRequest(
			Long cuentaId,
			BigDecimal monto,
			Boolean simularFallo,
			Boolean simularDuplicado) {
	}

	public record TransaccionDiariaView(
			long id,
			String fecha,
			BigDecimal monto,
			String tipo,
			boolean anomalia,
			String observacion) {
	}
}