import json
import logging
import os

import boto3
from botocore.exceptions import ClientError


LOGGER = logging.getLogger()
LOGGER.setLevel(logging.INFO)
SCALABLE_SERVICES = {"query-api", "command-api"}
SUSPEND_ALL_SCALING = {
    "DynamicScalingInSuspended": True,
    "DynamicScalingOutSuspended": True,
    "ScheduledScalingSuspended": True,
}


def stop_resources(cluster, services, database_identifier, autoscaling, ecs, rds):
    for service in services:
        if service in SCALABLE_SERVICES:
            autoscaling.register_scalable_target(
                ServiceNamespace="ecs",
                ResourceId=f"service/{cluster}/{service}",
                ScalableDimension="ecs:service:DesiredCount",
                MinCapacity=0,
                MaxCapacity=0,
                SuspendedState=SUSPEND_ALL_SCALING,
            )

    for service in services:
        ecs.update_service(cluster=cluster, service=service, desiredCount=0)

    try:
        rds.stop_db_instance(DBInstanceIdentifier=database_identifier)
        database_status = "stop-requested"
    except ClientError as error:
        details = error.response.get("Error", {})
        if details.get("Code") == "InvalidDBInstanceState" and "stopped" in details.get("Message", "").lower():
            database_status = "already-stopped"
        else:
            raise

    result = {"services": services, "database": database_status}
    LOGGER.info("Emergency cost stop completed: %s", result)
    return result


def handler(event, context):
    return stop_resources(
        os.environ["ECS_CLUSTER"],
        json.loads(os.environ["ECS_SERVICE_NAMES"]),
        os.environ["DATABASE_IDENTIFIER"],
        boto3.client("application-autoscaling"),
        boto3.client("ecs"),
        boto3.client("rds"),
    )
