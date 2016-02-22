#!/usr/bin/env bash
# build and copy interface code
set -xe
interface() {
   lein ring uberwar
   rsync -zvhi target/web4.war overseer@arnold:/usr/local/Cellar/jetty/9.3.7.v20160115/libexec/webapps/
   rsync -navhi --size-only /home/foranw/src/db_clojure/web4/resources/  overseer:/usr/local/Cellar/jetty/9.3.7.v20160115/libexec/resources --exclude '*.swp'
}

# copy database
database(){
   sudo -u postgres pg_dump lncddb -c > fulldb.$(date +%F)
   scp fulldb.$(date +%F) overseer:/usr/local/Cellar/jetty/9.3.7.v20160115/libexec/resources
   ssh overseer psql lncddb <  fulldb.$(date +%F)
}

if [ -z "$1" ]; then 
  interface && database
else
  eval $1
fi
