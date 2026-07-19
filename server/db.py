import sqlite3
import typing


class Database:
    def __init__(self, path: str):
        self.db = sqlite3.connect(path, autocommit=True)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute(
            "CREATE TABLE IF NOT EXISTS pads (id TEXT NOT NULL PRIMARY KEY, etag TEXT NOT NULL, value BYTES NOT NULL, last_access DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL)"
        )

    def close(self):
        self.db.close()

    def create_pad(self, pad_id: str, data: bytes, data_etag: str) -> bool:
        """Returns False and do nothing if the pad already exists."""
        try:
            self.db.execute("INSERT INTO pads(id, etag, value) VALUES (?, ?, ?)", (pad_id, data_etag, data))
        except sqlite3.IntegrityError as e:
            if e.sqlite_errorcode == sqlite3.SQLITE_CONSTRAINT_PRIMARYKEY:
                return False  # the pad exists
            raise
        return True

    def update_pad(self, pad_id: str, data: bytes, data_etag: str, expected_etag: str) -> bool:
        """Returns False and do nothing if the pad does not exist or doesn't have the expected etag."""
        result = self.db.execute(
            "UPDATE pads SET etag = ?, value = ?, last_access = CURRENT_TIMESTAMP WHERE id = ? AND etag = ?",
            (data_etag, data, pad_id, expected_etag),
        )
        return result.rowcount == 1

    def get_pad(self, pad_id: str) -> typing.Optional[typing.Tuple[bytes, str]]:
        self.db.execute("UPDATE pads SET last_access = CURRENT_TIMESTAMP WHERE id = ?", (pad_id,))
        return self.db.execute("SELECT value, etag FROM pads WHERE id = ?", (pad_id,)).fetchone()

    def delete_pad(self, pad_id: str) -> bool:
        result = self.db.execute("DELETE FROM pads WHERE id = ?", (pad_id,))
        return result.rowcount == 1
