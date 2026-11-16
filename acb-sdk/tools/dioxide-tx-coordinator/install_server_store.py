"""Install only the new coordinator tables/config on the Relayer host; never starts chain transactions."""
import argparse
import json
import os
import re
import secrets
from pathlib import Path
from urllib.parse import urlsplit

import pymysql
import yaml


def private_write(path, text):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as output:
        output.write(text)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--relayer-config", required=True)
    parser.add_argument("--schema-file", required=True)
    parser.add_argument("--checkpoint-hash", required=True)
    parser.add_argument("--checkpoint-height", type=int, required=True)
    parser.add_argument("--network-id", required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    settings = yaml.safe_load(Path(args.relayer_config).read_text())["spring"]["datasource"]
    uri = urlsplit(settings["url"].removeprefix("jdbc:"))
    database = uri.path.lstrip("/")
    if not re.fullmatch("[A-Za-z0-9_]+", database):
        raise RuntimeError("unexpected database name")
    connection = pymysql.connect(host=uri.hostname, port=uri.port or 3306, user=settings["username"],
                                 password=settings["password"], database=database, autocommit=True)
    root = Path("/etc/antchain-bridge")
    config_path = root / "dioxide-tx.properties"
    password_path = root / "dioxide-tx.password"
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT table_name FROM information_schema.tables WHERE table_schema=%s AND table_name LIKE 'bridge_tx_%%'", (database,))
            tables = [row[0] for row in cursor.fetchall()]
            cursor.execute("SELECT COUNT(*) FROM mysql.user WHERE user='crosschain_tx' AND host='%'")
            existing_user = cursor.fetchone()[0]
            print(json.dumps({"database": database, "existingTables": tables, "existingUser": bool(existing_user),
                              "apply": args.apply, "configExists": config_path.exists()}), flush=True)
            if not args.apply:
                return
            if tables or existing_user or config_path.exists() or password_path.exists():
                raise RuntimeError("existing coordination state found; inspect instead of overwriting")
            root.mkdir(mode=0o700, parents=True, exist_ok=True)
            password = secrets.token_urlsafe(36)
            # Keep credential recoverable even if a later DDL fails; never print it.
            private_write(str(password_path), password + "\n")
            sql = Path(args.schema_file).read_text()
            sql = "\n".join(line for line in sql.splitlines() if not line.lstrip().startswith("--"))
            for statement in sql.split(";"):
                if statement.strip():
                    cursor.execute(statement)
            cursor.execute("CREATE USER 'crosschain_tx'@'%%' IDENTIFIED BY %s", (password,))
            for table in ["bridge_tx_account", "bridge_tx_submission"]:
                cursor.execute(f"GRANT SELECT,INSERT,UPDATE ON `{database}`.`{table}` TO 'crosschain_tx'@'%'")
            private_write(str(config_path), (
                f"networkId={args.network_id}\ncheckpointHeight={args.checkpoint_height}\n"
                f"checkpointHash={args.checkpoint_hash}\n"
                f"jdbcUrl=jdbc:mysql://{uri.hostname}:{uri.port or 3306}/{database}?connectTimeout=5000&socketTimeout=30000&useSSL=false\n"
                f"user=crosschain_tx\npasswordFile={password_path}\n"
            ))
            print(json.dumps({"installed": True, "tables": ["bridge_tx_account", "bridge_tx_submission"],
                              "config": str(config_path), "mode": "0600"}))
    finally:
        connection.close()


if __name__ == "__main__":
    main()
