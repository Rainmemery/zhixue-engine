#!/usr/bin/env python3
import subprocess
import os
import sys

sql_file = '/tmp/init_model_group.sql'

# Read SQL file
with open(sql_file, 'r') as f:
    sql_content = f.read()

print(f"Read SQL file: {len(sql_content)} bytes")

# Try to find mysql client
clients_to_try = [
    '/usr/bin/mysql',
    '/usr/local/bin/mysql',
    '/opt/mysql/bin/mysql',
    '/usr/bin/mariadb',
    '/usr/local/bin/mariadb',
]

mysql_cmd = None
for client in clients_to_try:
    if os.path.exists(client):
        mysql_cmd = client
        break

if not mysql_cmd:
    # Try which command
    try:
        result = subprocess.run(['which', 'mysql'], capture_output=True, text=True)
        if result.returncode == 0 and result.stdout.strip():
            mysql_cmd = result.stdout.strip()
    except:
        pass

if not mysql_cmd:
    print("ERROR: MySQL client not found!")
    print("Searching for available database clients...")
    
    # List what's available
    subprocess.run(['ls', '-la', '/usr/bin/*sql*'], shell=False)
    sys.exit(1)

print(f"Using MySQL client: {mysql_cmd}")

# Execute SQL
proc = subprocess.Popen(
    [mysql_cmd, '-u', 'root', '-p123456', '-h', '127.0.0.1', '-P', '3306'],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True
)

try:
    stdout, stderr = proc.communicate(input=sql_content, timeout=30)
    
    print("=" * 50)
    print("OUTPUT:")
    print(stdout if stdout else "(no output)")
    
    if stderr:
        print("\nERRORS/WARNINGS:")
        print(stderr)
    
    print(f"\nExit code: {proc.returncode}")
    
except subprocess.TimeoutExpired:
    proc.kill()
    print("ERROR: Command timed out after 30 seconds")
    sys.exit(1)