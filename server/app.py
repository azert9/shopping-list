import hashlib
import json
import os
import re

import flask
import werkzeug.exceptions

from app_error import *
from db import Database

DB_PATH = os.getenv("DB_PATH")

app = flask.Flask(__name__)

pad_id_REGEX = re.compile(r"^[a-zA-Z0-9_-]{1,100}$")

IF_MATCH_REGEX = re.compile(r"^\s*(W/)?\"([^\"]*)\"\s*(,\s*(W/)?\"[^\"]*\"\s*)*$")
assert IF_MATCH_REGEX.match('"etag"')
assert IF_MATCH_REGEX.match('W/"etag"')
assert IF_MATCH_REGEX.match('"etag1" , W/"etag2","fsqdfds"')


def compute_etag(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def get_db() -> Database:
    db = getattr(flask.g, "_database", None)
    if db is None:
        db = Database(DB_PATH)
        flask.g._database = db
    return db


@app.teardown_appcontext
def cleanup(_):
    db = getattr(flask.g, "_database", None)
    if db is not None:
        db.close()


@app.errorhandler(AppError)
def handle_app_error(error: AppError):
    resp = flask.Response(status=error.http_status_code, content_type="application/json")
    resp.set_data(json.dumps({"error": {"msg": error.msg}}))
    return resp


@app.errorhandler(werkzeug.exceptions.HTTPException)
def handle_http_error(error: werkzeug.exceptions.HTTPException):
    if error.code is None:
        return flask.Response(status=500)
    if error.code is not None and error.code // 100 == 4:
        return handle_app_error(AppError.bad_request(error.code))
    else:
        return handle_app_error(AppError(http_status_code=error.code, msg="HTTP error."))


@app.route("/v1/store/<pad_id>", methods=["PUT"])
def put_pad(pad_id: str):
    if not pad_id_REGEX.match(pad_id):
        raise AppError.bad_request()

    if flask.request.content_type != "application/octet-stream":
        # Unsupported Media Type
        raise AppError.bad_request(415)

    if_match_header = flask.request.headers.get("If-Match")

    db = get_db()

    if if_match_header is None:
        if not db.create_pad(pad_id, flask.request.data, compute_etag(flask.request.data)):
            # the list was existing, so we need a precondition for updating
            raise AppError(http_status_code=428, msg="This list already exist.")
        created = True
    else:
        match = IF_MATCH_REGEX.match(if_match_header)
        if match is None or match[1] is not None or match[3] is not None:
            # regex fail, weak ETag, or multiple ETags
            raise AppError(http_status_code=501, msg="Unsupported or invalid If-Match header.")
        etag = match[2]

        if not db.update_pad(pad_id, flask.request.data, compute_etag(flask.request.data), etag):
            # Precondition failed
            raise AppError(http_status_code=412, msg="The shopping list was modified since last fetched.")

        created = False

    return flask.Response(
        status=201 if created else 204,
        headers={
            "Location": f"/v1/{pad_id}",
            "ETag": f'"{compute_etag(flask.request.data)}"',
        },
    )


@app.route("/v1/store/<pad_id>", methods=["GET"])
def get_pad(pad_id: str):
    if not pad_id_REGEX.match(pad_id):
        raise AppError.bad_request()

    db = get_db()

    row = db.get_pad(pad_id)
    if row is None:
        raise AppError.pad_not_found()
    value, etag = row

    return flask.Response(
        value,
        status=200,
        content_type="application/octet-stream",
        headers={
            "ETag": f'"{etag}"',
        },
    )


@app.route("/v1/store/<pad_id>", methods=["DELETE"])
def delete_pad(pad_id: str):
    if not pad_id_REGEX.match(pad_id):
        raise AppError.bad_request()

    db = get_db()

    if db.delete_pad(pad_id):
        return flask.Response(status=204)
    else:
        raise AppError.pad_not_found()
