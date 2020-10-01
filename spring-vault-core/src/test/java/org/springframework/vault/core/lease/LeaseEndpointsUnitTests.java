package org.springframework.vault.core.lease;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.web.client.RestOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LeaseEndpointsUnitTests {

	@Mock
	Lease oldLease;

	@Mock
	RestOperations restOperations;

	@Captor
	ArgumentCaptor<HttpEntity<Map<String, String>>> httpEntityCaptor;

	@Test
	@DisplayName("LeaseEndpoints.Legacy uses PUT /sys/renew to renew a lease")
	void legacyRenewsUsingSysRenew() {

		Map<String, Object> vaultResponseBody = new HashMap<>();
		vaultResponseBody.put("lease_id", "new_lease");
		vaultResponseBody.put("lease_duration", 90L);
		vaultResponseBody.put("renewable", false);
		when(restOperations.exchange(eq("sys/renew"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
				.thenReturn(new ResponseEntity<>(vaultResponseBody, HttpStatus.OK));

		when(oldLease.getLeaseId()).thenReturn("old_lease");
		when(oldLease.getLeaseDuration()).thenReturn(Duration.ofSeconds(70));

		Lease renewedLease = LeaseEndpoints.Legacy.renew(oldLease, restOperations);

		verify(restOperations).exchange(eq("sys/renew"), eq(HttpMethod.PUT), httpEntityCaptor.capture(), eq(Map.class));

		Map<String, String> actualRequestBodyParams = httpEntityCaptor.getValue().getBody();
		assertThat(actualRequestBodyParams).containsOnly(
				entry("lease_id", "old_lease"),
				entry("increment", "70"));

		assertThat(renewedLease.getLeaseId()).isEqualTo("new_lease");
		assertThat(renewedLease.getLeaseDuration()).isEqualTo(Duration.ofSeconds(90));
		assertThat(renewedLease.isRenewable()).isFalse();

		verifyNoMoreInteractions(restOperations);
	}

	@Test
	@DisplayName("LeaseEndpoints.Legacy uses PUT /sys/revoke to revoke a lease")
	void legacyRevokesUsingSysRevoke() {

		when(oldLease.getLeaseId()).thenReturn("old_lease");

		LeaseEndpoints.Legacy.revoke(oldLease, restOperations);

		verify(restOperations).exchange(eq("sys/revoke"), eq(HttpMethod.PUT), httpEntityCaptor.capture(),
				eq(Map.class), eq("old_lease"));

		Map<String, String> actualRequestBodyParams = httpEntityCaptor.getValue().getBody();
		assertThat(actualRequestBodyParams).containsOnly(entry("lease_id", "old_lease"));

		verifyNoMoreInteractions(restOperations);
	}

	@Test
	@SuppressWarnings("deprecation")
	@DisplayName("LeaseEndpoints.SysLeases uses PUT /sys/leases/renew to renew a lease")
	void sysLeasesRenewsUsingSysLeasesRenew() {

		Map<String, Object> vaultResponseBody = new HashMap<>();
		vaultResponseBody.put("lease_id", "new_lease");
		vaultResponseBody.put("lease_duration", 90L);
		vaultResponseBody.put("renewable", false);
		when(restOperations.exchange(eq("sys/leases/renew"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
				.thenReturn(new ResponseEntity<>(vaultResponseBody, HttpStatus.OK));

		when(oldLease.getLeaseId()).thenReturn("old_lease");
		when(oldLease.getLeaseDuration()).thenReturn(Duration.ofSeconds(70));

		Lease renewedLease = LeaseEndpoints.SysLeases.renew(oldLease, restOperations);

		verify(restOperations).exchange(eq("sys/leases/renew"), eq(HttpMethod.PUT), httpEntityCaptor.capture(),
				eq(Map.class));

		Map<String, String> actualRequestBodyParams = httpEntityCaptor.getValue().getBody();
		assertThat(actualRequestBodyParams).containsOnly(
				entry("lease_id", "old_lease"),
				entry("increment", "70"));

		assertThat(renewedLease.getLeaseId()).isEqualTo("new_lease");
		assertThat(renewedLease.getLeaseDuration()).isEqualTo(Duration.ofSeconds(90));
		assertThat(renewedLease.isRenewable()).isFalse();

		verifyNoMoreInteractions(restOperations);
	}

	@Test
	@SuppressWarnings("deprecation")
	@DisplayName("LeaseEndpoints.SysLeases uses PUT /sys/leases/revoke to revoke a lease")
	void sysLeasesRevokesUsingSysLeasesRevoke() {

		when(oldLease.getLeaseId()).thenReturn("old_lease");

		LeaseEndpoints.SysLeases.revoke(oldLease, restOperations);

		verify(restOperations).exchange(eq("sys/leases/revoke"), eq(HttpMethod.PUT), httpEntityCaptor.capture(),
				eq(Map.class), eq("old_lease"));

		Map<String, String> actualRequestBodyParams = httpEntityCaptor.getValue().getBody();
		assertThat(actualRequestBodyParams).containsOnly(entry("lease_id", "old_lease"));

		verifyNoMoreInteractions(restOperations);
	}

	@Test
	@DisplayName("LeaseEndpoints.Leases uses PUT /sys/leases/renew to renew a lease")
	void leasesRenewsUsingSysLeasesRenew() {

		Map<String, Object> vaultResponseBody = new HashMap<>();
		vaultResponseBody.put("lease_id", "new_lease");
		vaultResponseBody.put("lease_duration", 90L);
		vaultResponseBody.put("renewable", false);
		when(restOperations.exchange(eq("sys/leases/renew"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
				.thenReturn(new ResponseEntity<>(vaultResponseBody, HttpStatus.OK));

		when(oldLease.getLeaseId()).thenReturn("old_lease");
		when(oldLease.getLeaseDuration()).thenReturn(Duration.ofSeconds(70));

		Lease renewedLease = LeaseEndpoints.Leases.renew(oldLease, restOperations);

		verify(restOperations).exchange(eq("sys/leases/renew"), eq(HttpMethod.PUT), httpEntityCaptor.capture(),
				eq(Map.class));

		Map<String, String> actualRequestBodyParams = httpEntityCaptor.getValue().getBody();
		assertThat(actualRequestBodyParams).containsOnly(
				entry("lease_id", "old_lease"),
				entry("increment", "70"));

		assertThat(renewedLease.getLeaseId()).isEqualTo("new_lease");
		assertThat(renewedLease.getLeaseDuration()).isEqualTo(Duration.ofSeconds(90));
		assertThat(renewedLease.isRenewable()).isFalse();

		verifyNoMoreInteractions(restOperations);
	}

	@Test
	@DisplayName("LeaseEndpoints.Leases uses PUT /sys/leases/revoke to revoke a lease")
	void leasesRevokesUsingSysLeasesRevoke() {

		when(oldLease.getLeaseId()).thenReturn("old_lease");

		LeaseEndpoints.Leases.revoke(oldLease, restOperations);

		verify(restOperations).exchange(eq("sys/leases/revoke"), eq(HttpMethod.PUT), httpEntityCaptor.capture(),
				eq(Map.class), eq("old_lease"));

		Map<String, String> actualRequestBodyParams = httpEntityCaptor.getValue().getBody();
		assertThat(actualRequestBodyParams).containsOnly(entry("lease_id", "old_lease"));

		verifyNoMoreInteractions(restOperations);
	}

	@Test
	@DisplayName("LeaseEndpoints.LeasesRevokedByPrefix uses PUT /sys/leases/renew to renew a lease")
	void leasesRevokedByPrefixRenewsUsingSysLeasesRenew() {

		Map<String, Object> vaultResponseBody = new HashMap<>();
		vaultResponseBody.put("lease_id", "new_lease");
		vaultResponseBody.put("lease_duration", 90L);
		vaultResponseBody.put("renewable", false);
		when(restOperations.exchange(eq("sys/leases/renew"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
				.thenReturn(new ResponseEntity<>(vaultResponseBody, HttpStatus.OK));

		when(oldLease.getLeaseId()).thenReturn("old_lease");
		when(oldLease.getLeaseDuration()).thenReturn(Duration.ofSeconds(70));

		Lease renewedLease = LeaseEndpoints.LeasesRevokedByPrefix.renew(oldLease, restOperations);

		verify(restOperations).exchange(eq("sys/leases/renew"), eq(HttpMethod.PUT), httpEntityCaptor.capture(),
				eq(Map.class));

		Map<String, String> actualRequestBodyParams = httpEntityCaptor.getValue().getBody();
		assertThat(actualRequestBodyParams).containsOnly(
				entry("lease_id", "old_lease"),
				entry("increment", "70"));

		assertThat(renewedLease.getLeaseId()).isEqualTo("new_lease");
		assertThat(renewedLease.getLeaseDuration()).isEqualTo(Duration.ofSeconds(90));
		assertThat(renewedLease.isRenewable()).isFalse();

		verifyNoMoreInteractions(restOperations);
	}

	@Test
	@DisplayName("LeaseEndpoints.LeasesRevokedByPrefix uses PUT /sys/leases/revoke-prefix/{prefix} to revoke a lease")
	void leasesRevokedByPrefixRevokesUsingSysLeasesRevokePrefix() {

		when(oldLease.getLeaseId()).thenReturn("my/old/lease");

		LeaseEndpoints.LeasesRevokedByPrefix.revoke(oldLease, restOperations);

		verify(restOperations).put("sys/leases/revoke-prefix/my/old/lease", null);

		verifyNoMoreInteractions(restOperations);
	}
}
