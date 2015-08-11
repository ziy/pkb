DATA_DIR=../../../data
while read ID QUERY; do
  wget -E -H -U "Mozilla/5.0 (X11; Linux i686; rv:28.0) Gecko/20100101 Firefox/28.0" http://www.bing.com/search?q="$QUERY" -O $DATA_DIR/bingrp/$ID.html
  sleep 2
done < $DATA_DIR/query.tsv

