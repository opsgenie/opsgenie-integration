# -*- coding: utf-8 -*-
"""
Create/Close an alert in OpsGenie
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
.. versionadded:: 2017.7.2
This state is useful for creating or closing alerts in OpsGenie during state runs.
.. code-block:: yaml
    ﻿used_space:
      disk.status:
        - name: /
        - maximum: 79%
        - minimum: 20%

    opsgenie_create_action_sender:
      opsgenie.create_alert:
        - api_key: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
        - reason: 'Disk capacity is out of designated range.'
        - name: disk.status
        - onfail:
          - disk: used_space

    opsgenie_close_action_sender:
      opsgenie.close_alert:
        - api_key: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX
        - name: disk.status
        - require:
          - disk: used_space

"""

import salt.exceptions
import logging
import inspect

log = logging.getLogger(__name__)


def create_alert(name=None, api_key=None, reason=None, action_type="Create"):
    _, _, _, values = inspect.getargvalues(inspect.currentframe())
    log.info("Arguments values:" + str(values))

    ret = {
        'result': '',
        'name': '',
        'changes': '',
        'comment': ''
    }

    if api_key is None or reason is None:
        raise salt.exceptions.SaltInvocationError(
            'API Key or Reason cannot be None.')

    if __opts__['test'] == True:
        ret['comment'] = 'Test: ' + action_type + ' alert request will be processed using the API Key="{0}".'.format(api_key)

        # Return ``None`` when running with ``test=true``.
        ret['result'] = None

        return ret

    response_status_code, response_text = __salt__['opsgenie.post_data'](
        api_key=api_key,
        name=name,
        reason=reason,
        action_type=action_type
    )

    if 200 <= response_status_code < 300:
        log.info(
            "POST Request has succeeded with message:" + response_text + " status code:" + str(response_status_code))
        ret['comment'] = action_type + ' alert request will be processed using the API Key="{0}".'.format(api_key)
        ret['result'] = True
    else:
        log.error("POST Request has failed with error:" + response_text + " status code:" + str(response_status_code))
        ret['result'] = False

    return ret


def close_alert(name=None, api_key=None, reason="Conditions are met.", action_type="Close"):
    return create_alert(name, api_key, reason, action_type)
