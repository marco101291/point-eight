package com.pointeight.match.domain;

import com.pointeight.shared.domain.ResourceNotFoundException;

public class MatchNotFoundException extends ResourceNotFoundException {

  public MatchNotFoundException(MatchId id) {
    super("No existe el match " + id);
  }
}
