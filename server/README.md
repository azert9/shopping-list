## Running locally

```shell
python3 -m venv .venv
source venv/bin/activate
pip install -r ./requirements.txt
DB_PATH=/tmp/shopping-list-server.sqlite flask --app app.py run
```

## Protocol

### `GET /v1/store/{pad_id}`

Get a pad. The response is of type `application/octet-stream`.

### `PUT /v1/store/{pad_id}`

Create or update a pad:
- The request must have a content-type of `application/octet-stream`.
- If no `If-Match` header is provided and the list exists, this endpoint will return a code 428.
- On success, the response will have a code of 201 or 204 and will contain an `ETag` header.


### `DELETE /v1/store/{pad_id}`

Delete a pad.
