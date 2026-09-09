#!/bin/sh
set -eu

create_queue() {
  awslocal sqs create-queue --queue-name "$1" >/dev/null
}

queue_arn() {
  awslocal sqs get-queue-attributes --queue-url "$1" --attribute-names QueueArn --query 'Attributes.QueueArn' --output text
}

create_queue flash-booking-expiration-dlq
create_queue flash-booking-notification-dlq

expiration_dlq_url="$(awslocal sqs get-queue-url --queue-name flash-booking-expiration-dlq --query QueueUrl --output text)"
notification_dlq_url="$(awslocal sqs get-queue-url --queue-name flash-booking-notification-dlq --query QueueUrl --output text)"

expiration_attributes="$(printf '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"3\\"}"}' "$(queue_arn "$expiration_dlq_url")")"
notification_attributes="$(printf '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"3\\"}"}' "$(queue_arn "$notification_dlq_url")")"

awslocal sqs create-queue --queue-name flash-booking-expiration --attributes "$expiration_attributes" >/dev/null
awslocal sqs create-queue --queue-name flash-booking-notification --attributes "$notification_attributes" >/dev/null
