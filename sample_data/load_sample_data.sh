#!/usr/bin/env bash
# Simple loader to push sample JSON into local DynamoDB
AWS="aws --endpoint-url http://localhost:8000 --region us-east-1 dynamodb"

echo "Loading students..."
for row in $(jq -c '.[]' sample_data/students.json); do
  studentId=$(echo $row | jq -r '.studentId')
  name=$(echo $row | jq -r '.name')
  email=$(echo $row | jq -r '.email')
  activeEnrollments=$(echo $row | jq -r '.activeEnrollments')
  waitlistCount=$(echo $row | jq -r '.waitlistCount')
  $AWS put-item --table-name Students --item "{\"studentId\":{\"S\":\"$studentId\"},\"name\":{\"S\":\"$name\"},\"email\":{\"S\":\"$email\"},\"activeEnrollments\":{\"N\":\"$activeEnrollments\"},\"waitlistCount\":{\"N\":\"$waitlistCount\"}}"
done

echo "Loading courses..."
for row in $(jq -c '.[]' sample_data/courses.json); do
  courseId=$(echo $row | jq -r '.courseId')
  name=$(echo $row | jq -r '.courseName')
  maxSeats=$(echo $row | jq -r '.maxSeats')
  startDate=$(echo $row | jq -r '.startDate')
  endDate=$(echo $row | jq -r '.endDate')
  latestEnrollmentBy=$(echo $row | jq -r '.latestEnrollmentBy')
  $AWS put-item --table-name Courses --item "{\"courseId\":{\"S\":\"$courseId\"},\"courseName\":{\"S\":\"$name\"},\"maxSeats\":{\"N\":\"$maxSeats\"},\"currentEnrolledCount\":{\"N\":\"0\"},\"startDate\":{\"S\":\"$startDate\"},\"endDate\":{\"S\":\"$endDate\"},\"latestEnrollmentBy\":{\"S\":\"$latestEnrollmentBy\"},\"waitlistSeq\":{\"N\":\"0\"}}"
done
