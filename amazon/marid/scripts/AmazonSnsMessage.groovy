import com.ifountain.opsgenie.client.marid.http.HTTPRequest
import com.ifountain.opsgenie.client.util.JsonUtils
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult
import org.jfree.chart.axis.DateAxis
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
            max:"Max",
            min:"Min",
    ]
    public static final String MESSAGE_TYPE_SUBSCRIPTION_CONFIRMATION = "SubscriptionConfirmation";
    public static final String MESSAGE_TYPE_NOTIFICATION = "Notification";
    public static final String MESSAGE_TYPE_HEADER_NAME = "x-amz-sns-message-type";
    HTTPRequest request;
    Map snsMessage;
    Map couldwatchMessage;
    Map config;
    public AmazonSnsMessage(HTTPRequest request, Map config = [:]){
        this.request = request;
        this.config = config;
        snsMessage = getSnsMessage();
        if(MESSAGE_TYPE_NOTIFICATION == request.getHeader(MESSAGE_TYPE_HEADER_NAME)){
            couldwatchMessage = getCloudwatchMessageMap();
        }
    }

    public boolean confirmSubscription() throws Exception{
        String subscribeURL = (String) couldwatchMessage.get("SubscribeURL");
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
        return "${couldwatchMessage.AlarmDescription?couldwatchMessage.AlarmDescription:""}. ${couldwatchMessage.NewStateReason}"
    }
    public Map getCloudwatchAlertDetails() throws IOException {
        def details = [AlarmName:couldwatchMessage.AlarmName, StateChangeTime:couldwatchMessage.StateChangeTime,
                Region:couldwatchMessage.Region, MetricName:couldwatchMessage.Trigger.MetricName,
                Namespace:couldwatchMessage.Trigger.Namespace
        ]
        couldwatchMessage.Trigger.Dimensions.each{dimension->
            details[dimension.name] = dimension.value
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
        out.flush();
        return out.toByteArray()
    }


    public List<Map> createMetricGraphs(){
        def metrics = getMetrics()
        def statisticNameFromMessage = couldwatchMessage.Trigger.Statistic;
        def statisticName = STATISTICS_NAMES[statisticNameFromMessage.toLowerCase()]
        List<Map> graphs = new ArrayList<Map>();
        AmazonCloudWatch cloudWatch = new AmazonCloudWatchClient(new BasicAWSCredentials(config.AWS_ACCESS_KEY, config.AWS_SECRET_KEY))
        def endDate = new Date(System.currentTimeMillis());
        def startDate = new Date(Math.max(endDate.getTime() - TimeUnit.DAYS.toMillis(config.LAST_N_DAYS), endDate.getTime() - 1440*config.STAT_PERIOD*1000));
        metrics.each{String metricNameToBeProcessed->
            ListMetricsRequest listMetricsRequest = new ListMetricsRequest();
            listMetricsRequest.setMetricName(metricNameToBeProcessed)
            ListMetricsResult listMetricsResult = cloudWatch.listMetrics(listMetricsRequest)
            listMetricsResult.metrics.each{metric->
                GetMetricStatisticsRequest req = new GetMetricStatisticsRequest();
                req.setEndTime(endDate);
                req.setStartTime(startDate);
                req.setPeriod(config.STAT_PERIOD)
                req.setDimensions(metric.getDimensions())
                req.setNamespace(metric.getNamespace())
                def imageName = "${metric.getNamespace()}_${metric.getMetricName()}".replaceAll("\\W", "_")
                imageName = "${imageName}.png"
                req.setStatistics([statisticName])
                req.setMetricName(metric.getMetricName());
                GetMetricStatisticsResult res = cloudWatch.getMetricStatistics(req)
                def data = createGraphFromMetricResults(res, statisticName)
                graphs.add([data:data, name:imageName])
            }
        }
        return graphs;
    }

    private List getMetrics(){
        def metricName = couldwatchMessage.Trigger.MetricName;
        def additionalMetrics = config?.ADDITIONAL_METRICS?.get(metricName);
        def metrics = [metricName];
        if(additionalMetrics) metrics.addAll(additionalMetrics);
        return metrics;
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
        return JsonUtils.parse(messageStr);
    }

}
