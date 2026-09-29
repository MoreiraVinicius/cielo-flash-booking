#!/bin/sh
# Must be LF: LocalStack executes this bind-mounted file inside a Linux shell.
set -eu

queue_arn() {
  awslocal sqs get-queue-attributes --queue-url "$1" --attribute-names QueueArn --query 'Attributes.QueueArn' --output text
}

awslocal sqs create-queue --queue-name flash-booking-expiration-dlq >/dev/null
awslocal sqs create-queue --queue-name flash-booking-notification-dlq >/dev/null
awslocal sqs create-queue --queue-name flash-booking-reservation-to-owner-dlq >/dev/null
awslocal sqs create-queue --queue-name flash-booking-reservation-from-owner-dlq >/dev/null

expiration_dlq_url="$(awslocal sqs get-queue-url --queue-name flash-booking-expiration-dlq --query QueueUrl --output text)"
notification_dlq_url="$(awslocal sqs get-queue-url --queue-name flash-booking-notification-dlq --query QueueUrl --output text)"
reservation_to_owner_dlq_url="$(awslocal sqs get-queue-url --queue-name flash-booking-reservation-to-owner-dlq --query QueueUrl --output text)"
reservation_from_owner_dlq_url="$(awslocal sqs get-queue-url --queue-name flash-booking-reservation-from-owner-dlq --query QueueUrl --output text)"

expiration_attributes="$(printf '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"3\\"}"}' "$(queue_arn "$expiration_dlq_url")")"
notification_attributes="$(printf '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"3\\"}"}' "$(queue_arn "$notification_dlq_url")")"
reservation_to_owner_attributes="$(printf '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"5\\"}","ReceiveMessageWaitTimeSeconds":"20","VisibilityTimeout":"60"}' "$(queue_arn "$reservation_to_owner_dlq_url")")"
reservation_from_owner_attributes="$(printf '{"RedrivePolicy":"{\\"deadLetterTargetArn\\":\\"%s\\",\\"maxReceiveCount\\":\\"5\\"}","ReceiveMessageWaitTimeSeconds":"20","VisibilityTimeout":"60"}' "$(queue_arn "$reservation_from_owner_dlq_url")")"

awslocal sqs create-queue --queue-name flash-booking-expiration --attributes "$expiration_attributes" >/dev/null
awslocal sqs create-queue --queue-name flash-booking-notification --attributes "$notification_attributes" >/dev/null
awslocal sqs create-queue --queue-name flash-booking-reservation-to-owner --attributes "$reservation_to_owner_attributes" >/dev/null
awslocal sqs create-queue --queue-name flash-booking-reservation-from-owner --attributes "$reservation_from_owner_attributes" >/dev/null
