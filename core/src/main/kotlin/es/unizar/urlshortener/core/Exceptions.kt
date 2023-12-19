package es.unizar.urlshortener.core

/**
 * Exception thrown when a url does not follow a supported schema.
 */
class InvalidUrlException(url: String) : Exception("[$url] does not follow a supported schema")

/**
 * Exception thrown when a redirection is not found.
 */
class RedirectionNotFound(key: String) : RuntimeException("[$key] is not known")

/**
 * Exception thrown when a QR is not found.
 */
class QrNotFound(key: String) : RuntimeException("[$key] does not have a QR")

/**
 * Exception thrown when a QR is not ready yet.
 */
class QrNotReady(key: String) : RuntimeException("[$key] Qr is not ready yet")
