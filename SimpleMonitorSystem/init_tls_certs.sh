CURR_DIR="$(cd `dirname $0`; pwd)"

if [ ! -d ${CURR_DIR}/tls_certs ]; then
	mkdir -p ${CURR_DIR}/tls_certs
fi

openssl genrsa -out ${CURR_DIR}/tls_certs/monitor-system.key 2048 > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "failed to generate monitor-system.key"
    exit 1
fi
openssl pkcs8 -topk8 -inform pem -in ${CURR_DIR}/tls_certs/monitor-system.key -nocrypt -out ${CURR_DIR}/tls_certs/monitor-system_pkcs8.key
if [ $? -ne 0 ]; then
    echo "failed to generate pkcs8 monitor-system.key"
    exit 1
fi
mv ${CURR_DIR}/tls_certs/monitor-system_pkcs8.key ${CURR_DIR}/tls_certs/monitor-system.key
echo "generate monitor-system.key successfully"

openssl req -new -x509 -days 36500 -key ${CURR_DIR}/tls_certs/monitor-system.key -out ${CURR_DIR}/tls_certs/monitor-system.crt -subj "/C=CN/ST=mykey/L=mykey/O=mykey/OU=mykey/CN=ACB-monitor-system"
if [ $? -ne 0 ]; then
    echo "failed to generate monitor-system.crt"
    exit 1
fi
echo "generate monitor-system.crt successfully"