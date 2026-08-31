package com.pointeight.user.infrastructure;

import com.pointeight.shared.infrastructure.PageResponse;
import com.pointeight.user.application.DeleteUserUseCase;
import com.pointeight.user.application.RegisterUserUseCase;
import com.pointeight.user.application.UpdateUserProfileUseCase;
import com.pointeight.user.application.UserQueries;
import com.pointeight.user.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Adapter de entrada HTTP del aggregate User. Traduce a casos de uso, sin lógica propia. */
@RestController
@RequestMapping("/api/users")
public class UserController {

  private final RegisterUserUseCase registerUser;
  private final UpdateUserProfileUseCase updateProfile;
  private final DeleteUserUseCase deleteUser;
  private final UserQueries queries;

  public UserController(
      RegisterUserUseCase registerUser,
      UpdateUserProfileUseCase updateProfile,
      DeleteUserUseCase deleteUser,
      UserQueries queries) {
    this.registerUser = registerUser;
    this.updateProfile = updateProfile;
    this.deleteUser = deleteUser;
    this.queries = queries;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse register(@Valid @RequestBody RegisterUserRequest request) {
    return UserResponse.from(
        registerUser.execute(request.toProfile(), request.toSimulationParameters()));
  }

  @GetMapping
  public PageResponse<UserResponse> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    var content = queries.page(page, size).stream().map(UserResponse::from).toList();
    return PageResponse.of(content, page, size, queries.total());
  }

  @GetMapping("/{id}")
  public UserResponse byId(@PathVariable String id) {
    return UserResponse.from(queries.byId(UserId.of(id)));
  }

  @PatchMapping("/{id}")
  public UserResponse update(
      @PathVariable String id, @Valid @RequestBody UpdateUserRequest request) {
    return UserResponse.from(updateProfile.execute(UserId.of(id), request.toProfile()));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    deleteUser.execute(UserId.of(id));
    return ResponseEntity.noContent().build();
  }
}
