Sean Youngstone, V00589570

To compile the programs, run the following commands:
javac HTTPClient.java
javac HTTPServer.java

Sample commands to run HTTPServer.java:
java HTTPServer 10373
java HTTPServer 10001

Sample GET request commands using HTTPClient.java:
java HTTPClient http://info.cern.ch
java HTTPClient http://172.18.233.74:10373
java HTTPClient http://172.18.233.74:10373/index.html
java HTTPClient http://172.18.233.74:10373/mydir/index.html

Sample PUT request commands using HTTPClient.java:
java HTTPClient PUT http://172.18.233.74:10373 index.html
java HTTPClient PUT http://172.18.233.74:10373/mydir index.html
java HTTPClient PUT http://172.18.233.74:10373/mydir localdir/index.html

(Note that the directories mydir and localdir do not currently exist in the zip file)

IMPORTANT NOTES:

* I was successful at testing these programs when they were both in separate directories
  on my local machine, and when they were both in separate directories on egr-v-cmsc440-1.
  I was not successful at testing them with the client on one machine and the server on a
  different machine. I'm not sure if this was due to a programming bug, lack of permissions,
  or not using the correct IP addresses for the remote machines.

* HTTPServer.java and HTTPClient.java are only designed to receive content which is made
  up of characters. They cannot handle images or other non-textual data.

* HTTPServer.java uses the "Content-Length" header to recognize the end of a PUT request.
  I originally attempted to recognize the end of the request using the BufferedReader methods:
  - ready() - should return false at end of request
  - read() - should return -1 instead of another character at end of request
  - readLine() - should return NULL instead of another line at the end of request
  However, none of these methods functioned as expected, so I fell back to using the
  "Content-Length" header. This means that HTTPClient.java generates one additional header
  beyond the ones described in the project specifications, and HTTPServer.java cannot
  successfully complete a PUT request unless the request contains the "Content-Length" header.

* HTTPServer.java will return "606 FAILED File NOT Created" if it receives a PUT request
  which includes directories that do not currently exist. For example, if the directory
  containing the running copy of HTTPServer.java has one subdirectory named mydir, then
  the following commands will result in "200 OK File Created":
  - "java HTTPClient PUT http://172.18.233.74:10373 filename.html"
  - "java HTTPClient PUT http://172.18.233.74:10373/mydir filename.html"
  But this command will result in "606 FAILED File NOT Created":
  - "java HTTPClient PUT http://172.18.233.74:10373/mydir2 filename.html"

* If it receives a GET request with only the path "/", HTTPServer.java will attempt to return
  a file titled "index.html". If no such file exists, it will return "404 Not Found".
  If it receives any other path which does not point to an existing file, it will also return
  "404 Not Found".

* If HTTPClient.java receives content from the path "/", it will save that content as
  "index.html". Otherwise, it will use the last portion of the URL as the filename. For
  example, data received from http://info.cern.ch/hypertext/WWW/TheProject.html will be saved
  as "TheProject.html". Data received from http://info.cern.ch/hypertext/ will simply be
  saved as "hypertext" (no file extension).

* I have included a sample "index.html" file in my zip obtained from http://info.cern.ch

