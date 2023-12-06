package es.unizar.urlshortener.core

class InvalidUrlException(url: String) : Exception("[$url] does not follow a supported schema")

class RedirectionNotFound(key: String) : RuntimeException("[$key] is not known")

class QrNotFound(key: String) : RuntimeException("[$key] does not have a QR")

class QrNotReady(key: String) : RuntimeException("[$key] Qr is not ready yet")
