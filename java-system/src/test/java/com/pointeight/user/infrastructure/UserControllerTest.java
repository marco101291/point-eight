package com.pointeight.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pointeight.user.application.DeleteUserUseCase;
import com.pointeight.user.application.RegisterUserUseCase;
import com.pointeight.user.application.UpdateUserProfileUseCase;
import com.pointeight.user.application.UserQueries;
import com.pointeight.user.domain.AttachmentStyle;
import com.pointeight.user.domain.CommunicationProfile;
import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.SimulationParameters;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The contract that can't be broken: Layer 2 comes in through the API and never goes back out.
 *
 * <p>Verified at two levels — structurally (the record doesn't declare those fields) and against
 * the real JSON the endpoint returns.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

  /** Names that must never appear in a response. */
  private static final List<String> CAPA_2 =
      List.of(
          "attachment",
          "criticism",
          "contempt",
          "defensiveness",
          "stonewalling",
          "communicationprofile",
          "infidelity",
          "relationshiphistory",
          "addiction",
          "stressbaseline",
          "commitmentpace");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private RegisterUserUseCase registerUser;
  @MockBean private UpdateUserProfileUseCase updateProfile;
  @MockBean private DeleteUserUseCase deleteUser;
  @MockBean private UserQueries queries;

  private static User sampleUser() {
    Profile profile =
        new Profile(
            31,
            Gender.FEMALE,
            Set.of(Gender.MALE),
            SeekingType.LONG_TERM,
            "Buenos Aires",
            "arquitecta",
            List.of("cine", "escalada"));
    SimulationParameters params =
        new SimulationParameters(
            AttachmentStyle.DISORGANIZED,
            0.93,
            new CommunicationProfile(0.9, 0.85, 0.7, 0.8),
            true,
            7,
            true,
            0.88,
            0.77);
    return User.register(profile, params, Clock.systemUTC());
  }

  @Test
  @DisplayName("UserResponse doesn't declare a single Layer 2 field")
  void elRecordNoTieneDondeFiltrar() {
    List<String> declared =
        Arrays.stream(UserResponse.class.getRecordComponents())
            .map(RecordComponent::getName)
            .map(name -> name.toLowerCase(Locale.ROOT))
            .toList();

    assertThat(declared)
        .as("fields declared by UserResponse")
        .noneSatisfy(name -> assertThat(CAPA_2).anySatisfy(hidden -> assertThat(name).contains(hidden)));
  }

  @Test
  @DisplayName("registration accepts Layer 2 but doesn't return it")
  void elAltaNoDevuelveCapa2() throws Exception {
    when(registerUser.execute(any(), any())).thenReturn(sampleUser());

    String requestBody =
        """
        {
          "age": 31,
          "gender": "FEMALE",
          "seekingGenders": ["MALE"],
          "seekingType": "LONG_TERM",
          "city": "Buenos Aires",
          "profession": "arquitecta",
          "hobbies": ["cine", "escalada"],
          "attachmentStyle": "DISORGANIZED",
          "attachmentIntensity": 0.93,
          "communicationProfile": {
            "criticism": 0.9, "contempt": 0.85, "defensiveness": 0.7, "stonewalling": 0.8
          },
          "infidelityHistory": true,
          "relationshipHistory": 7,
          "activeAddiction": true
        }
        """;

    String response =
        mockMvc
            .perform(
                post("/api/users").contentType(MediaType.APPLICATION_JSON).content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.city").value("Buenos Aires"))
            .andExpect(jsonPath("$.age").value(31))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertNoCapa2(response);
  }

  @Test
  @DisplayName("the query by id doesn't return it either")
  void laConsultaNoDevuelveCapa2() throws Exception {
    User user = sampleUser();
    when(queries.byId(any())).thenReturn(user);

    String response =
        mockMvc
            .perform(get("/api/users/{id}", user.id().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cumulativeConfidenceScore").value(0.0))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertNoCapa2(response);
  }

  @Test
  @DisplayName("neither does the listing")
  void elListadoNoDevuelveCapa2() throws Exception {
    when(queries.page(0, 20)).thenReturn(List.of(sampleUser()));
    when(queries.total()).thenReturn(1L);

    String response =
        mockMvc
            .perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertNoCapa2(response);
  }

  @Test
  @DisplayName("a nonexistent user gives 404, not 500")
  void usuarioInexistente() throws Exception {
    UserId missing = UserId.newId();
    when(queries.byId(any())).thenThrow(new UserNotFoundException(missing));

    mockMvc
        .perform(get("/api/users/{id}", missing.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource not found"));
  }

  @Test
  @DisplayName("a malformed id gives 400")
  void idInvalido() throws Exception {
    mockMvc.perform(get("/api/users/{id}", "no-soy-un-uuid")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("an age under 18 is rejected right at the edge")
  void edadInvalida() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new RegisterUserRequest(
                17, Gender.MALE, Set.of(Gender.FEMALE), SeekingType.CASUAL, "Córdoba", "docente",
                List.of(), null, null, null, null, null, null, null, null));

    mockMvc
        .perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  private static void assertNoCapa2(String json) {
    String lower = json.toLowerCase(Locale.ROOT);
    assertThat(CAPA_2)
        .as("la respuesta fue: %s", json)
        .noneSatisfy(hidden -> assertThat(lower).contains(hidden));
  }
}
