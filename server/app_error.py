import typing
from dataclasses import dataclass


@dataclass
class AppError(Exception):
    http_status_code: int
    msg: str = None

    @staticmethod
    def bad_request(http_status_code: int = 400, msg: str = "Bad request.") -> AppError:
        return AppError(
            http_status_code=http_status_code,
            msg=msg,
        )

    @staticmethod
    def pad_not_found() -> AppError:
        return AppError(
            http_status_code=404,
        )
