import java.text.*
import java.nio.file.*
import groovy.transform.Sortable
import static groovy.io.FileType.*

// parse tomcat access log 
// based on default Tomcat access log format with a couple changes:
// - change time format to ISO-8601 + milliseconds
// - added millisecond response time in []'s
// from $CATALINA_HOME/conf/server.xml:
//        <Valve className="org.apache.catalina.valves.AccessLogValve" directory="logs"
//               prefix="localhost_access_log." suffix=".txt"
//               pattern="%h %l %u %{YYYY-MM-dd'T'HH:mm:ss.SSS}t &quot;%r&quot; %s %b [%D]" />
// sample:
// 0:0:0:0:0:0:0:1 - - 2021-02-12T00:33:01.473 "GET /jasperserver-pro/rest_v2/hypermedia/workflows?parentName=main HTTP/1.1" 200 5221 [340]

class LogParser {
    // format has to match what you specify in Tomcat conf/server.xml (see below)
    // not currently parsing the date
    // def dateformat = new SimpleDateFormat("YYYY-MM-dd'T'HH:mm:ss.SSS")
    // filter out requests with these suffixes
    def staticFileTypes = ["png", "css", "jpg", "js", "ico"]
    // filter out these URL's
    def ignoreURLs = [ ]
    // only show requests with time more than this
    def minRespTime = 10
    // filter out these response codes
    def ignoreRespCodes = [ 302 ]
    // look for URLs for the webapp, then take it off the front of the URL
    def webAppName = "/jasperserver-pro"
    
    // path of log to parse
    def filename
    // regex to parse access log (needs to match format defined in server.xml (see above)
    def logmatch = /(.+) - - (.*) \"(.*)\" (\d+) (.+) \[(\d+)\]/

    // parse a line into fields (currently grabbing six) using regex
    def parseLine(line) {
        def fields
        // create a matcher, return its first element (capturing regexes are two-level arrays)
        def m = (line.trim() =~ logmatch)
        return m ? m[0][1..6] : null
    }
    
    // process file (directory support later)
    def process() {
        def p = Path.of(filename).toAbsolutePath()
        if (! Files.exists(p)) {
             println "$filename not found"
        } else if (Files.isDirectory(p)) {
            // tbd
            println "traversing directory not implemented yet"
        } else {
            processFile(p)
        }
    }

    // read in one file and parse it
    // create start and end events
    // sort those events
    // output to a file
    def processFile(logpath) {
        // create output file in current dir
        def outpath = Path.of(logpath.fileName.toString() + ".parsed")
        outpath.withPrintWriter { outw ->
            // read lines, parse them, filter out stuff you want to ignore, print selected fields
            logpath.eachLine { line ->
                def fields = parseLine(line)
                if (fields) {
                    def (ip, timestamp, req, respCode, length, respTime) = fields
                    def ms = respTime as int
                    def code = respCode as int
                    if (length == "-") { length = 0 }
                    // filter URLs, response codes, response time
                    if (! (req in ignoreURLs) && ! (code in ignoreRespCodes) && ms >= minRespTime) {
                        def (op, url, proto) = req.split(" ")
                        // filter out static content or URLs not matching webapp
                        if (! statContent(url) && url.startsWith(webAppName)) {
                            // chop off webapp
                            url = url.substring(webAppName.size())
                            outw.println "$timestamp $op $code $ms $length $url"
                        }
                    }
                } else {
                    println "what? $line"
                }
            }
        }
        null
    }
    
    // return true if url matches one of the static url types to ignore
    def statContent(url) {
        def m = (url =~ /.*\.(\w+)/)
        if (m) {
            def suffix = m[0][1]
            suffix in staticFileTypes
        }
    }
}

def logfile
if (args.length > 0) {
    parser = new LogParser(filename: args[0])
    parser.process()
} else {
   throw new RuntimeException("usage: parse-access-log-simple <file>")
}

