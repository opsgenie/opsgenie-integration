import com.amazonaws.services.cloudwatch.model.Dimension
import com.ifountain.opsgenie.client.marid.http.HTTPRequest
import com.ifountain.opsgenie.client.util.JsonUtils
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult
import org.jfree.chart.axis.DateAxis
import org.jfree.chart.axis.DateTickUnit
import org.jfree.chart.axis.DateTickUnitType

import java.text.SimpleDateFormat
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer
import org.jfree.data.xy.XYDataset
import org.jfree.data.time.TimeSeriesCollection
import org.jfree.data.time.TimeSeries
import org.jfree.data.time.Minute
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.axis.DateTickMarkPosition
import org.jfree.chart.JFreeChart
import org.jfree.chart.ChartUtilities
import com.amazonaws.services.cloudwatch.AmazonCloudWatch
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient
import com.amazonaws.auth.BasicAWSCredentials
import java.util.concurrent.TimeUnit
import com.amazonaws.services.cloudwatch.model.ListMetricsRequest
import com.amazonaws.services.cloudwatch.model.ListMetricsResult
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
public class AmazonSnsMessage {
    public static final Map STATISTICS_NAMES = [
            average:"Average",
            maximum:"Maximum",
            minimum:"Minimum",
            sum:"Sum",
            sample_count:"SampleCount"
    ]
    //Sum, Maximum, Minimum, SampleCount, Average

    public static final String MESSAGE_TYPE_SUBSCRIPTION_CONFIRMATION = "SubscriptionConfirmation";
    public static final String MESSAGE_TYPE_NOTIFICATION = "Notification";
    public static final String MESSAGE_TYPE_HEADER_NAME = "x-amz-sns-message-type";
    static List ALERT_DETAILS_PROPERTYNAMES = ["AlarmName", "Region", "StateChangeTime","NewStateValue","OldStateValue"]
    public static final String OK_STATE= "OK";

    HTTPRequest request;
    Map snsMessage;
    Map cloudwatchMessage;
    Map config;
    public AmazonSnsMessage(HTTPRequest request, Map config = [:]){
        this.request = request;
        this.config = config;
        snsMessage = getSnsMessage();
        if(MESSAGE_TYPE_NOTIFICATION == request.getHeader(MESSAGE_TYPE_HEADER_NAME)){
            cloudwatchMessage = getCloudwatchMessageMap();
        }
    }

    public boolean confirmSubscription() throws Exception{
        String subscribeURL = (String) snsMessage.get("SubscribeURL");
        HttpURLConnection connection = (HttpURLConnection) new URL(subscribeURL).openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.connect();
        return connection.getResponseCode() == 200;
    }

    public String getMessageStr(){
        return (String) snsMessage.get("Message");
    }

    public String getSubject(){
        return (String) snsMessage.get("Subject");
    }


    public String getCloudwatchAlertDescription() throws IOException {
        return "${cloudwatchMessage.AlarmDescription?cloudwatchMessage.AlarmDescription:""}. ${cloudwatchMessage.NewStateReason}"
    }

    public Map getCloudwatchMessage(){
        return this.cloudwatchMessage;
    }

    public Map getCloudwatchAlertDetails() throws IOException {
        def details = [:];
        ALERT_DETAILS_PROPERTYNAMES.each {propName ->
            details[propName]=cloudwatchMessage[propName];
        }
        Object trigger = cloudwatchMessage.get("Trigger");
        if(trigger instanceof Map){
            trigger.each{ propName, value ->
                def triggerKey = "Trigger_"+propName;
                details.put(triggerKey, value);
            }
        }
        return details;
    }

    public String getRequestType(){
        return request.getHeader(MESSAGE_TYPE_HEADER_NAME);
    }

    public byte[] createGraphFromMetricResults(GetMetricStatisticsResult metricResult, statisticsName){
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        def datapoint = metricResult.getDatapoints().sort{it.timestamp};
        DateAxis domainAxis = new DateAxis();
        domainAxis.setDateFormatOverride(new SimpleDateFormat("dd-MM HH:mm"))
        domainAxis.setTickUnit(new DateTickUnit(DateTickUnitType.HOUR, 6));
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
        out.flush();
        return out.toByteArray()
    }


    public List<Map> createMetricGraphs(){
        def statisticNameFromMessage = cloudwatchMessage.Trigger.Statistic;
        def statisticName = STATISTICS_NAMES[statisticNameFromMessage.toLowerCase()]
        List<Map> graphs = new ArrayList<Map>();
        AmazonCloudWatch cloudWatch = new AmazonCloudWatchClient(new BasicAWSCredentials(config.AWS_ACCESS_KEY, config.AWS_SECRET_KEY))

        def metricName = cloudwatchMessage.Trigger.MetricName;
        def metricPeriod = cloudwatchMessage.Trigger.Period
        def metricNamespace = cloudwatchMessage.Trigger.Namespace
        def metricUnit = cloudwatchMessage.Trigger.Unit
        def metricDimensions = [];
        cloudwatchMessage.Trigger.Dimensions.each{ dimension ->
            metricDimensions.add(new Dimension(name:dimension.name, value:dimension.value));
        }

        def endDate = new Date(System.currentTimeMillis());
        def startDate = new Date(endDate.getTime() - TimeUnit.HOURS.toMillis(config.GRAPH_LAST_N_HOURS));

        GetMetricStatisticsRequest req = new GetMetricStatisticsRequest();
        req.setEndTime(endDate);
        req.setStartTime(startDate);
        req.setPeriod(metricPeriod)
        req.setDimensions(metricDimensions)
        req.setNamespace(metricNamespace)
        def imageName = "${metricNamespace}_${metricName}".replaceAll("\\W", "_")
        imageName = "${imageName}.png"
        req.setStatistics([statisticName])
        req.setMetricName(metricName);
        if(metricUnit) req.setUnit(metricUnit);

        GetMetricStatisticsResult res = cloudWatch.getMetricStatistics(req)
        def data = createGraphFromMetricResults(res, statisticName)
        graphs.add([data:data, name:imageName])

        return graphs;
    }


    private Map getSnsMessage(){
        String contentAsString = request.getContent();
        if (contentAsString != null) {
            return JsonUtils.parse(contentAsString);
        } else {
            throw new Exception("No request content received");
        }
    }

    private Map getCloudwatchMessageMap() throws IOException {
        String messageStr = getMessageStr()
        println messageStr
        return JsonUtils.parse(messageStr);
    }

}
