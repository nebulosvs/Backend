package com.banco.xyz.bff.security;

import org.springframework.stereotype.Service;

import com.banco.xyz.bff.service.BankDataQueryService;

@Service
public class AtmPinService {

	private final BankDataQueryService service;

	public AtmPinService(
			BankDataQueryService service) {

		this.service = service;
	}

	public boolean isValidPin(
			long technicalAccountId,
			String pin) {

		if (pin == null || pin.isBlank()) {
			return false;
		}

		return service
				.accountById(technicalAccountId)
				.map(account ->
						pin.equals(
								expectedPinForAccount(
										account.cuentaId())))
				.orElse(false);
	}

	private String expectedPinForAccount(
			String cuentaId) {

		if (cuentaId == null || cuentaId.isBlank()) {
			return "";
		}

		try {

			long numericAccountId =
					Long.parseLong(cuentaId);

			long value =
					Math.abs(
							numericAccountId % 10_000);

			return String.format(
					"%04d",
					value);

		} catch (NumberFormatException ex) {

			return "";
		}
	}
}