import importlib.util
import pathlib
import unittest

from botocore.exceptions import ClientError


MODULE_PATH = pathlib.Path(__file__).with_name("cost_emergency_stop.py")
SPEC = importlib.util.spec_from_file_location("cost_emergency_stop", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class RecordingClient:
    def __init__(self):
        self.calls = []


class AutoscalingClient(RecordingClient):
    def register_scalable_target(self, **kwargs):
        self.calls.append(kwargs)


class EcsClient(RecordingClient):
    def update_service(self, **kwargs):
        self.calls.append(kwargs)


class RdsClient(RecordingClient):
    def stop_db_instance(self, **kwargs):
        self.calls.append(kwargs)


class StoppedRdsClient(RdsClient):
    def stop_db_instance(self, **kwargs):
        self.calls.append(kwargs)
        raise ClientError(
            {"Error": {"Code": "InvalidDBInstanceState", "Message": "DBInstance is already stopped"}},
            "StopDBInstance",
        )


class CostEmergencyStopTest(unittest.TestCase):
    def test_stops_services_freezes_autoscaling_and_stops_database(self):
        autoscaling = AutoscalingClient()
        ecs = EcsClient()
        rds = RdsClient()

        result = MODULE.stop_resources("cluster-arn", "cluster-name", ["query-api", "command-api", "worker"], "postgres-id", autoscaling, ecs, rds)

        self.assertEqual("stop-requested", result["database"])
        self.assertEqual(
            [
                {"ServiceNamespace": "ecs", "ResourceId": "service/cluster-name/query-api", "ScalableDimension": "ecs:service:DesiredCount", "MinCapacity": 0, "MaxCapacity": 0, "SuspendedState": {"DynamicScalingInSuspended": True, "DynamicScalingOutSuspended": True, "ScheduledScalingSuspended": True}},
                {"ServiceNamespace": "ecs", "ResourceId": "service/cluster-name/command-api", "ScalableDimension": "ecs:service:DesiredCount", "MinCapacity": 0, "MaxCapacity": 0, "SuspendedState": {"DynamicScalingInSuspended": True, "DynamicScalingOutSuspended": True, "ScheduledScalingSuspended": True}},
            ],
            autoscaling.calls,
        )
        self.assertEqual(
            [
                {"cluster": "cluster-arn", "service": "query-api", "desiredCount": 0},
                {"cluster": "cluster-arn", "service": "command-api", "desiredCount": 0},
                {"cluster": "cluster-arn", "service": "worker", "desiredCount": 0},
            ],
            ecs.calls,
        )
        self.assertEqual([{"DBInstanceIdentifier": "postgres-id"}], rds.calls)

    def test_treats_an_already_stopped_database_as_idempotent(self):
        result = MODULE.stop_resources("cluster-arn", "cluster-name", ["query-api", "command-api", "worker"], "postgres", AutoscalingClient(), EcsClient(), StoppedRdsClient())

        self.assertEqual("already-stopped", result["database"])


if __name__ == "__main__":
    unittest.main()
