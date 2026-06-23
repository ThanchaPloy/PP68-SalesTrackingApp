package com.pp68.backend.domain.exception

sealed class DomainException(message: String) : Exception(message)
class NotFoundException(message: String) : DomainException(message)
class UnauthorizedException(message: String) : DomainException(message)
class ConflictException(message: String) : DomainException(message)
class ForbiddenException(message: String) : DomainException(message)