package com.pointeight.auth.domain;

import com.pointeight.user.domain.UserId;

/**
 * Issues a bearer token proving a user's identity for subsequent requests. A port: the domain and
 * application layers don't know or care whether it's a JWT, an opaque token, or anything else.
 */
public interface TokenIssuer {

  String issue(UserId userId);
}
