#!/usr/bin/env bash
# Creates DynamoDB tables on local DynamoDB (endpoint http://localhost:8000)
AWS="aws --endpoint-url http://localhost:8000 --region us-east-1 dynamodb"

echo "Creating Students table..."
$AWS create-table --table-name Students \
  --attribute-definitions AttributeName=studentId,AttributeType=S \
  --key-schema AttributeName=studentId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

echo "Creating Courses table..."
$AWS create-table --table-name Courses \
  --attribute-definitions AttributeName=courseId,AttributeType=S \
  --key-schema AttributeName=courseId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

echo "Creating Enrollments table..."
$AWS create-table --table-name Enrollments \
  --attribute-definitions AttributeName=studentId,AttributeType=S AttributeName=courseId,AttributeType=S \
  --key-schema AttributeName=studentId,KeyType=HASH AttributeName=courseId,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST

echo "Creating Waitlists table... (PK=courseId, SK=seq)"
$AWS create-table --table-name Waitlists \
  --attribute-definitions AttributeName=courseId,AttributeType=S AttributeName=seq,AttributeType=N \
  --key-schema AttributeName=courseId,KeyType=HASH AttributeName=seq,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST

echo "Creating StudentLogs table..."
$AWS create-table --table-name StudentLogs \
  --attribute-definitions AttributeName=logId,AttributeType=S \
  --key-schema AttributeName=logId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

echo "Done."
