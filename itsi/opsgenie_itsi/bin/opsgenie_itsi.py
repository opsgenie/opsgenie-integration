import requests
import sys
from json import dumps
from splunk.clilib.bundle_paths import make_splunkhome_path

sys.path.append(make_splunkhome_path(['etc', 'apps', 'opsgenie_itsi', 'lib']))
sys.path.append(make_splunkhome_path(['etc', 'apps', 'SA-ITOA', 'lib']))

from ITOA.setup_logging import setup_logging
from itsi.event_management.sdk.custom_event_action_base import CustomEventActionBase

OPSGENIE_LOG_FILE = 'opsgenie_itsi.log'

OPSGENIE_URL = "opsgenie_url"

CORRELATION_KEYS = [
    'alert_level',
    'alert_severity',
    'alert_value',
    'alert_color',
    'alert_period',
    'all_info',
    'change_type',
    'composite_kpi_id',
    'composite_kpi_name',
    'color',
    'description',
    'drilldown_search_search',
    'drilldown_uri',
    'enabled',
    'entity_title',
    'event_description',
    'event_id',  # required
    'event_identifier_hash',
    'gs_kpi_id',
    'gs_service_id',
    'health_score',
    'host',
    'index',
    'latest_alert_level',
    'linecount',
    'indexed_is_service_aggregate',
    'indexed_is_service_max_severity_event',
    'indexed_itsi_kpi_id',
    'indexed_itsi_service_id',
    'is_service_aggregate',
    'is_service_in_maintenance',
    'is_service_max_severity_event',
    'kpi',
    'kpi_name',
    'kpi_urgency',
    'kpibasesearch',
    'kpiid',
    'occurances',
    'orig_index',
    'orig_severity',
    'orig_sourcetype',
    'owner',
    'percentage',
    'scoretype',
    'search_name',
    'search_now',
    'search_type',
    'service_ids',
    'service_kpi_ids',
    'service_name',
    'severity',
    'severity_label',
    'severity_value',
    'source',
    'statement',
    'splunk_server',
    'splunk_server_group',
    'tag',
    'time',
    'timeDiff',
    'timeDiffInMin',
    'title',
    'total_occurrences',
    'urgency',
]


class OpsGenieITSI(CustomEventActionBase):
    def __init__(self, settings):
        self.logger = setup_logging(OPSGENIE_LOG_FILE, 'opsgenie.itsi.event.action')

        super(OpsGenieITSI, self).__init__(settings, self.logger)

        config = self.get_config()

        self.result = self.settings.get('result')
        self.opsgenie_url = config[OPSGENIE_URL]
        self.priority = config['priority']

        self.logger.info(
            'opsgenie_url=%s priority=%s',
            'OPSGENIE_ITSI_INIT',
            self.opsgenie_url,
            self.priority
        )

    def get_prop_value(self, key, value):
        value_type = type(value)
        if value_type is list:
            return ','.join(value)
        elif value_type is str or value_type is unicode:
            return value
        elif value is None:
            return ''
        self.logger.warn('warning=INVALID_PROP_TYPE key=%s value_type=%s', key, value_type)
        return False

    def get_key_values_from_object(self, keys, source_object):
        result = {}
        for key in keys:
            value = self.get_prop_value(key, source_object.get(key))
            if value is not False:
                result[key] = value
        return result

    def get_notable_event_properties(self):
        return self.get_key_values_from_object(CORRELATION_KEYS, self.result)

    def send_opsgenie_event(self):
        payload = {}
        properties = self.get_notable_event_properties()
        for key in properties:
            payload[key] = properties[key]

        payload["priority"] = self.priority

        headers = {"Content-Type": "application/json"}

        return requests.post(self.opsgenie_url, data=dumps(payload), headers=headers)

    def get_correlation_event_id(self):
        return self.result.get('event_id')

    def execute(self):
        correlation_event_id = self.get_correlation_event_id()
        if correlation_event_id is None:
            raise Exception('No correlation event_id')

        try:
            response = self.send_opsgenie_event()
            if response.status_code < 200 or response.status_code > 299:
                self.logger.error('OpsGenie responded with HTTP status=%d' % response.status_code)
                raise Exception('Failed to execute send event actions.')

        except ValueError:
            pass
        except Exception, exception:
            self.logger.error('Failed to execute.')
            self.logger.exception(exception)
            sys.exit(1)
        return


if __name__ == '__main__':
    LOGGER = setup_logging(OPSGENIE_LOG_FILE, 'opsgenie.itsi')
    if len(sys.argv) > 1 and sys.argv[1] == '--execute':
        INPUT_PARAMS = sys.stdin.read()
        try:
            ITSI = OpsGenieITSI(INPUT_PARAMS)
            ITSI.execute()
        except Exception, exception:
            LOGGER.error('Failed to execute')
            LOGGER.exception(exception)
            raise exception
