package com.pointeight.user.infrastructure;

import com.pointeight.user.domain.AttachmentStyle;
import com.pointeight.user.domain.CommunicationProfile;
import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.SimulationParameters;
import com.pointeight.user.domain.TraitDerivation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

/**
 * Alta de usuario. Los campos de Capa 2 son opcionales y <b>write-only</b>: se aceptan acá, pero no
 * existe ningún DTO que los devuelva. Si no vienen, el Sistema los deriva del perfil.
 */
public record RegisterUserRequest(
    @Min(18) @Max(120) int age,
    @NotNull Gender gender,
    @NotEmpty Set<Gender> seekingGenders,
    SeekingType seekingType,
    @NotBlank String city,
    @NotBlank String profession,
    List<String> hobbies,
    // --- Capa 2, opcional ---
    AttachmentStyle attachmentStyle,
    Double attachmentIntensity,
    CommunicationProfile communicationProfile,
    Boolean infidelityHistory,
    Integer relationshipHistory,
    Boolean activeAddiction,
    Double stressBaseline,
    Double commitmentPaceExpectation) {

  public Profile toProfile() {
    return new Profile(age, gender, seekingGenders, seekingType, city, profession, hobbies);
  }

  /**
   * Capa 2 explícita, o {@code null} para que el aggregate aplique la derivación por defecto. Los
   * rasgos derivables caen a {@link TraitDerivation} campo por campo cuando no se informan.
   */
  public SimulationParameters toSimulationParameters() {
    boolean anyProvided =
        attachmentStyle != null
            || attachmentIntensity != null
            || communicationProfile != null
            || infidelityHistory != null
            || relationshipHistory != null
            || activeAddiction != null
            || stressBaseline != null
            || commitmentPaceExpectation != null;
    if (!anyProvided) {
      return null;
    }
    return new SimulationParameters(
        attachmentStyle == null ? AttachmentStyle.SECURE : attachmentStyle,
        attachmentIntensity == null ? 0.5 : attachmentIntensity,
        communicationProfile == null ? CommunicationProfile.neutral() : communicationProfile,
        infidelityHistory != null && infidelityHistory,
        relationshipHistory == null ? 0 : relationshipHistory,
        activeAddiction != null && activeAddiction,
        stressBaseline == null ? TraitDerivation.stressBaseline(profession, age) : stressBaseline,
        commitmentPaceExpectation == null
            ? TraitDerivation.commitmentPaceExpectation(age)
            : commitmentPaceExpectation);
  }
}
