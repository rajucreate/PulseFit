package com.pulsefit.facility;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pulsefit.facility.dto.FacilityRequest;
import com.pulsefit.facility.dto.FacilityResponse;
import com.pulsefit.facility.model.Facility;
import com.pulsefit.facility.model.FacilityStatus;
import com.pulsefit.facility.repository.FacilityRepository;
import com.pulsefit.facility.service.FacilityService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FacilityServiceTest {
  @Mock private FacilityRepository repository;
  private FacilityService service;

  @BeforeEach
  void setUp() {
    service = new FacilityService(repository);
  }

  @Test
  void deactivateKeepsTheRowAndMarksItInactive() {
    Facility facility =
        new Facility("Downtown", "1 Main", "Austin", "555", "6-22", 100, FacilityStatus.ACTIVE);
    setId(facility, 4L);
    when(repository.findById(4L)).thenReturn(Optional.of(facility));
    when(repository.save(facility)).thenReturn(facility);

    FacilityResponse response = service.deactivate(4L);

    assertEquals(FacilityStatus.INACTIVE, response.status());
    assertEquals(4L, response.id());
    verify(repository, never()).delete(any());
  }

  @Test
  void createTrimsNames() {
    when(repository.save(any(Facility.class)))
        .thenAnswer(
            invocation -> {
              Facility saved = invocation.getArgument(0);
              setId(saved, 1L);
              return saved;
            });
    FacilityResponse response =
        service.create(
            new FacilityRequest(
                "  North  ", "Addr", "Dallas", "555", "5-21", 80, FacilityStatus.ACTIVE));
    assertEquals("North", response.name());
    assertEquals(FacilityStatus.ACTIVE, response.status());
  }

  private void setId(Facility facility, Long id) {
    try {
      var field = Facility.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(facility, id);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
