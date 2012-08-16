import com.ifountain.opsgenie.client.util.JsonUtils
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest
import java.util.concurrent.TimeUnit
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest
import com.amazonaws.services.cloudwatch.model.ListMetricsResult
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import org.jfree.chart.axis.DateAxis
import java.text.SimpleDateFormat
import org.jfree.chart.axis.NumberAxis
import org.jfree.data.xy.XYDataset
import org.jfree.data.time.TimeSeriesCollection
import org.jfree.data.time.TimeSeries
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.axis.DateTickMarkPosition
import org.jfree.chart.JFreeChart
import org.jfree.chart.ChartUtilities
import org.jfree.data.time.Minute
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer
/********************** CONFIGURATION ************************/
recipients = "cloudwatchgroup"
ADDITIONAL_METRICS = [CPUUtilization:["DiskReadBytes", "NetworkIn", "NetworkOut" , "DiskWriteBytes"]]
AWS_ACCESS_KEY="AKIAJOP7BLKVKCBL2GDQ"
AWS_SECRET_KEY="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"
LAST_N_DAYS=10;
STAT_PERIOD = 300;
ATTACH_GRAPHS = true;
/*************************************************************/

/*********************************************/
STATISTICS_NAMES = [
        average:"Average",
        max:"Max",
        min:"Min",
]
/*********************************************/
Map snsMessage = getMessage(request);
if(AmazonSnsEndPointUtils.MESSAGE_TYPE_SUBSCRIPTION_CONFIRMATION.equals(AmazonSnsEndPointUtils.getRequestType(request))){
    AmazonSnsEndPointUtils.confirmSubscription(snsMessage);
}
else if(AmazonSnsEndPointUtils.MESSAGE_TYPE_NOTIFICATION.equals(AmazonSnsEndPointUtils.getRequestType(request))){
    def alertId = createAlert(snsMessage);
    if(ATTACH_GRAPHS){
        attachMetricGraphs(snsMessage, alertId);
    }
}

def createAlert(snsMessage){
    String alertDescription = AmazonSnsEndPointUtils.getCloudwatchAlertDescription(snsMessage);
    Map details = AmazonSnsEndPointUtils.getCloudwatchAlertDetails(snsMessage);
    String subject = AmazonSnsEndPointUtils.getSubject(snsMessage);
    def alertProps = [recipients:recipients, message:subject,  details:details, description:alertDescription]
    logger.warn("Creating alert with message ${subject}");
    def response = opsgenie.createAlert(alertProps)
    def alertId =  response.alertId;
    logger.warn("Alert is created with id :"+alertId);
    return alertId;
}

def attachMetricGraphs(snsMessage, alertId){
    def cloudwatchMessageMap = AmazonSnsEndPointUtils.getCloudwatchMessageMap(snsMessage)
    def metricName = cloudwatchMessageMap.Trigger.MetricName;
    def statisticNameFromMessage = cloudwatchMessageMap.Trigger.Statistic;
    logger.warn("Attaching graphs for ${metricName}");
    ByteArrayOutputStream bout = createMetricGraphs(metricName, statisticNameFromMessage);
    def response = opsgenie.attach([alertId:alertId, stream:new ByteArrayInputStream(bout.toByteArray()), fileName:"metrics.zip"])
    if(response.success){
        logger.warn("Successfully attached search results");
    }
}

def createMetricGraphs(metricNameComingWithSnsMessage, statisticNameFromMessage){
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ZipOutputStream zout = new ZipOutputStream(bout);
    AmazonCloudWatch cloudWatch = new AmazonCloudWatchClient(new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY))
    def additionalMetrics = ADDITIONAL_METRICS[metricNameComingWithSnsMessage];
    def metrics = [metricNameComingWithSnsMessage];
    if(additionalMetrics) metrics.addAll(additionalMetrics);
    def endDate = new Date(System.currentTimeMillis());
    def startDate = new Date(Math.max(endDate.getTime() - TimeUnit.DAYS.toMillis(100), endDate.getTime() - 1440*300*1000));
    def metricImageConfigs = [];
    metrics.each{String metricNameToBeProcessed->
        ListMetricsRequest listMetricsRequest = new ListMetricsRequest();
        listMetricsRequest.setMetricName(metricNameToBeProcessed)
        ListMetricsResult listMetricsResult = cloudWatch.listMetrics(listMetricsRequest)
        listMetricsResult.metrics.each{metric->
            GetMetricStatisticsRequest req = new GetMetricStatisticsRequest();
            req.setEndTime(endDate);
            req.setStartTime(startDate);
            req.setPeriod(STAT_PERIOD)
            req.setDimensions(metric.getDimensions())
            req.setNamespace(metric.getNamespace())
            def statisticName = STATISTICS_NAMES[statisticNameFromMessage.toLowerCase()]
            def imageName = "${metric.getNamespace()}_${metric.getMetricName()}".replaceAll("\\W", "_")
            imageName = "${imageName}.png"
            req.setStatistics([statisticName])
            req.setMetricName(metric.getMetricName());
            GetMetricStatisticsResult res = cloudWatch.getMetricStatistics(req)
            ZipEntry chartEntry = new ZipEntry(imageName);
            zout.putNextEntry(chartEntry);
            createGraphFromMetricResults(res, statisticName, zout);
            zout.closeEntry()
            metricImageConfigs << [name:imageName, title:"${statisticName} ${metric.getMetricName()} of ${metric.getNamespace()}"]
        }
    }

    String html = createHtml(metricImageConfigs, metricNameComingWithSnsMessage)
    ZipEntry chartEntry = new ZipEntry("index.html");
    zout.putNextEntry(chartEntry);
    zout.write(html.getBytes())
    zout.closeEntry();
    zout.close();
    return bout;
}

def createHtml(metricImageConfigs, metricName){
    StringBuffer html = new StringBuffer();
    html.append("""
        <html>
        <head>
            <title>${metricName} and Related Metrics</title>
        </head>
        <body>
            <table>
    """)
    for(int i=0; i < metricImageConfigs.size();){
        html.append("<tr>")
        3.times{
            def metricImageConfig = metricImageConfigs [i]
            if(metricImageConfig){
                html.append("""
                <td>
                    <div>${metricImageConfig.title}</div>
                    <img src="${metricImageConfig.name}">
                </td>
                """)
            }
            i++;
        }
        html.append("</tr>")
    }
    html.append("""
            </table>
        </body>
        </html>
    """)
    return html.toString();
}

def createGraphFromMetricResults(GetMetricStatisticsResult metricResult, statisticsName, OutputStream out){
    def datapoint = metricResult.getDatapoints().sort{it.timestamp};
    DateAxis domainAxis = new DateAxis();
    domainAxis.setDateFormatOverride(new SimpleDateFormat("dd-MM-yyyy"))
    NumberAxis rangeAxis = new NumberAxis("Number of Events");
    XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
    XYDataset dataset = new TimeSeriesCollection();
    TimeSeries timeSeries = new TimeSeries("Event Count", Minute.class);
    datapoint.each {dp->
        timeSeries.add(new Minute(dp.getTimestamp()), dp."get${statisticsName}"());
    }
    dataset.addSeries(timeSeries);
    XYPlot mainPlot = new XYPlot(dataset, domainAxis, rangeAxis, renderer);
    mainPlot.setDomainGridlinesVisible(true);
    domainAxis.setTickMarkPosition(DateTickMarkPosition.MIDDLE);

    JFreeChart chart = new JFreeChart(null, null, mainPlot, false);
    ChartUtilities.writeChartAsPNG(out, chart, 350, 200);
}

protected Map getMessage(request) throws Exception{
    String contentAsString = request.getContent();
    if (contentAsString != null) {
        return JsonUtils.parse(contentAsString);
    } else {
        throw new Exception("No request content received");
    }
}