# -*- coding: utf-8 -*-
"""
Module for sending data to OpsGenie

.. versionadded:: 2017.7.2

:configuration: This module can be used in Reactor System for
    posting data to OpsGenie as a remote-execution function.
    For example:
    .. code-block:: yaml
        ﻿opsgenie_event_poster:
          local.opsgenie.post_data:
            - tgt: 'salt-minion'
            - kwarg:
                name: event.reactor
                api_key: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
                reason: {{ data['data']['reason'] }}
                action_type: Create
"""

import json
import logging
import requests
import salt.exceptions

'''
    If you are using opsgenie from another domain
    you should update the line below
'''
API_ENDPOINT = "https://api.opsgenie.com/v1/json/saltstack?apiKey="

log = logging.getLogger(__name__)

def post_data(api_key=None, name='OpsGenie Execution Module', reason=None, action_type=None):
    if api_key is None or reason is None or action_type is None:
        raise salt.exceptions.SaltInvocationError(
            'API Key or Reason or Action Type cannot be None.')

    data = dict()
    data['name'] = name
    data['reason'] = reason
    data['actionType'] = action_type
    data['cpuModel'] = __grains__['cpu_model']
    data['cpuArch'] = __grains__['cpuarch']
    data['fqdn'] = __grains__['fqdn']
    data['host'] = __grains__['host']
    data['id'] = __grains__['id']
    data['kernel'] = __grains__['kernel']
    data['kernelRelease'] = __grains__['kernelrelease']
    data['master'] = __grains__['master']
    data['os'] = __grains__['os']
    data['saltPath'] = __grains__['saltpath']
    data['saltVersion'] = __grains__['saltversion']
    data['username'] = __grains__['username']
    data['uuid'] = __grains__['uuid']

    log.debug('Below data will be posted:\n' + str(data))
    log.debug('API Key:' + api_key + '\t API Endpoint:' + API_ENDPOINT)

    response = requests.post(url=API_ENDPOINT+api_key, data=json.dumps(data),
                             headers={'Content-Type': 'application/json'})
    return response.status_code, response.text