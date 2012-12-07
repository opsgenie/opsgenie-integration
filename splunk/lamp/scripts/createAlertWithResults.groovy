import java.text.SimpleDateFormat
import java.util.zip.GZIPInputStream
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.jfree.chart.ChartUtilities
import org.jfree.chart.JFreeChart
import org.jfree.chart.axis.DateAxis
import org.jfree.chart.axis.DateTickMarkPosition
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.XYBarRenderer
import org.jfree.data.time.Day
import org.jfree.data.time.TimeSeries
import org.jfree.data.time.TimeSeriesCollection
import org.jfree.data.xy.XYDataset
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import org.apache.commons.lang.StringEscapeUtils

numberOfEvents = System.getenv("SPLUNK_ARG_1");
searchTerms = System.getenv("SPLUNK_ARG_2");
queryString = System.getenv("SPLUNK_ARG_3");
nameOfSearch = System.getenv("SPLUNK_ARG_4");
reason = System.getenv("SPLUNK_ARG_5");
browserUrl = System.getenv("SPLUNK_ARG_6");
rawFilePath = System.getenv("SPLUNK_ARG_8");
SEARCH_RESULTS_TO_BE_SHOWN = 20;



def alertProps = [:]
alertProps.message = nameOfSearch + " has " + numberOfEvents + " events"
alertProps.recipients = "Operations"
alertProps.details = [:]
alertProps.details.searchTerms = searchTerms
alertProps.details.browserUrl = browserUrl
alertProps.details.queryString = queryString
alertProps.details.numberOfEvents = numberOfEvents
alertProps.details.nameOfSearch = nameOfSearch

logger.warn("Creating alert with message ${alertProps.message}");
def response = opsgenie.createAlert(alertProps)
def alertId =  response.alertId;
logger.warn("Alert is created with id :"+alertId);


logger.warn("Attaching search results");
def rawFile = new File(rawFilePath)
InputStream input = new FileInputStream(rawFile);
input = new GZIPInputStream(input);
CSVFormat format = CSVFormat.DEFAULT.withHeader();
CSVParser parser = new CSVParser(new InputStreamReader(input), format)
ByteArrayOutputStream bout = createZip(parser);
response = opsgenie.attach([alertId:alertId, stream:new ByteArrayInputStream(bout.toByteArray()), fileName:"results.zip"])
if(response.success){
    logger.warn("Successfully attached search results");
}


def createZip(CSVParser parser){
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ZipOutputStream zout = new ZipOutputStream(bout);
    ZipEntry chartEntry = new ZipEntry("chart.png");
    zout.putNextEntry(chartEntry);
    def records = new ArrayList(parser.records);
    createChart(records, zout);
    zout.closeEntry()
    ZipEntry htmlEntry = new ZipEntry("index.html");
    zout.putNextEntry(htmlEntry);
    createHtml(records, zout);
    zout.closeEntry()
    zout.close();
    return bout;
}
def createHtml(List records, OutputStream out){
    def df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    def details = new StringBuffer();
    for(int i=0; i < SEARCH_RESULTS_TO_BE_SHOWN && i < records.size(); i++){
        def rec = records[i];
        details.append("""
        <tr>
        <td width="15%"><span class="datestr">${df.format(new Date(Long.parseLong(rec.get("_time"))*1000))}</span></td>
        <td width="85%">${htmlEscape(rec.get("_raw"))}</td>
        </tr>
    """)
    }
    if(SEARCH_RESULTS_TO_BE_SHOWN < records.size()){
        details.append("""
        <tr>
        <td colspan="2"><a href="${browserUrl}">More Results</a></td>
        </tr>
    """)
    }
    def htmlContent = """
    <html>
    <head>
        <style>
            table{
                border:1px solid #cccccc;
                border-bottom:none;
                table-layout: fixed;
                width: 100%;
            }
            td, th {
                border-bottom:1px solid #cccccc;
                word-wrap:break-word;
            }
            th {
                background-color:#eeeeee;
            }
            .datestr{
                white-space:nowrap;
            }
        </style>
    </head>
    <body>
        <div  style="padding-bottom:5px;">
            <a href="${browserUrl}">Go to Splunk</a>
        </div>
        <div style="padding-bottom:20px;text-align:center;">
            <img src="chart.png">
        </div>
        <div style="text-align:center;">
            <h2>Search Results</h2>
        </div>
        <table  cellspacing="0">
            <thead>
                <tr  style="text-align:left;">
                    <th width="15%">Date</th>
                    <th width="85%">Line</th>
                </tr>
            </thead>
            <tbody>
            ${details.toString()}
            </tbody>
        </table>
    </body>
    </html>
"""
    out.write(htmlContent.toString().getBytes());
}

def createChart(List records, OutputStream out){
    DateAxis domainAxis = new DateAxis();
    domainAxis.setDateFormatOverride(new SimpleDateFormat("dd-MM-yyyy"))
    NumberAxis rangeAxis = new NumberAxis("Number of Events");
    XYBarRenderer renderer = new XYBarRenderer(0.3);
    XYDataset dataset = new TimeSeriesCollection();
    TimeSeries timeSeries = new TimeSeries("Event Count", Day.class);
    def dailyData = [:]
    def df = new SimpleDateFormat("yyyy-MM-dd")
    records.each {CSVRecord rec ->
        def indexTime = Long.parseLong(rec.get("_time"))*1000
        def lineCount = Integer.parseInt(rec.get("linecount"));
        def str = df.format(new Date(indexTime))
        def value = dailyData.get(str);
        if(value == null){
            value = 0;
        }
        value += lineCount;
        dailyData.put(str, value);
    }
    dailyData.each {dateStr, value->
        timeSeries.add(new Day(df.parse(dateStr)), value);
    }
    dataset.addSeries(timeSeries);


    XYPlot mainPlot = new XYPlot(dataset, domainAxis, rangeAxis, renderer);
    mainPlot.setDomainGridlinesVisible(true);
    domainAxis.setTickMarkPosition(DateTickMarkPosition.MIDDLE);

    JFreeChart chart = new JFreeChart(null, null, mainPlot, false);
    ChartUtilities.writeChartAsPNG(out, chart, 500, 200);
}

def htmlEscape(value){
    return StringEscapeUtils.escapeHtml(value)
}
