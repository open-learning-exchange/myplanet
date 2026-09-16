/// Port of `data/NetworkResult.kt`.
///
/// Kotlin's `sealed class NetworkResult<out T>` becomes a Dart sealed class, so
/// `switch` over it stays exhaustive at compile time the same way `when` did.
sealed class NetworkResult<T> {
  const NetworkResult();
}

class NetworkSuccess<T> extends NetworkResult<T> {
  const NetworkSuccess(this.data);

  final T data;
}

/// A response that arrived but was not successful (non-2xx).
class NetworkError<T> extends NetworkResult<T> {
  const NetworkError(this.code, this.message);

  final int? code;
  final String? message;
}

/// A request that never produced a response (socket error, timeout, parse failure).
class NetworkException<T> extends NetworkResult<T> {
  const NetworkException(this.error);

  final Object error;
}
