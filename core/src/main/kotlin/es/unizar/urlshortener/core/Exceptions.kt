package es.unizar.urlshortener.core

class InvalidUrlException(url: String) : Exception("[$url] does not follow a supported schema")

class RedirectionNotFound(key: String) : Exception("[$key] is not known")

class QrNotFound(key: String) : Exception("[$key] does not have a QR")

class QrNotReady(key: String) : Exception("[$key] Qr is not ready yet")
