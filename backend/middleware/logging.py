"""
middleware/logging.py
---------------------
Structured request/response logging middleware.

Logs: method, path, status code, and wall-clock latency for every request.
Keeps the format machine-parseable (key=value) so it can be piped to any
log aggregator (CloudWatch, Papertrail, etc.) without extra parsing config.
"""

import time
import logging
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

logger = logging.getLogger("cps.access")
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(name)s  %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%S",
)


class RequestLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        start = time.perf_counter()

        try:
            response: Response = await call_next(request)
        except Exception as exc:
            elapsed_ms = (time.perf_counter() - start) * 1000
            logger.error(
                f"method={request.method} path={request.url.path} "
                f"status=500 latency_ms={elapsed_ms:.1f} error={exc!r}"
            )
            raise

        elapsed_ms = (time.perf_counter() - start) * 1000
        level = logging.WARNING if response.status_code >= 400 else logging.INFO
        logger.log(
            level,
            f"method={request.method} path={request.url.path} "
            f"status={response.status_code} latency_ms={elapsed_ms:.1f}",
        )
        return response
