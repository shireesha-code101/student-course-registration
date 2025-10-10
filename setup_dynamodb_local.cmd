@echo off
setlocal

set ENDPOINT=http://localhost:8000
set REGION=us-east-1

echo ==============================================
echo 🚀 Setting up DynamoDB Local Tables
echo ==============================================

echo.
echo 🗑️ Deleting old tables (if any)...
aws dynamodb delete-table --table-name Student --endpoint-url %ENDPOINT% --region %REGION% >nul 2>&1
aws dynamodb delete-table --table-name EmailIndex --endpoint-url %ENDPOINT% --region %REGION% >nul 2>&1
aws dynamodb delete-table --table-name Course --endpoint-url %ENDPOINT% --region %REGION% >nul 2>&1
aws dynamodb delete-table --table-name Enrollment --endpoint-url %ENDPOINT% --region %REGION% >nul 2>&1

timeout /t 2 >nul

echo.
echo ==============================================
echo 📦 Creating Tables
echo ==============================================

echo Creating Student table...
aws dynamodb create-table --table-name Student ^
  --attribute-definitions AttributeName=studentId,AttributeType=S ^
  --key-schema AttributeName=studentId,KeyType=HASH ^
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 ^
  --endpoint-url %ENDPOINT% --region %REGION%

echo Creating EmailIndex table...
aws dynamodb create-table --table-name EmailIndex ^
  --attribute-definitions AttributeName=email,AttributeType=S ^
  --key-schema AttributeName=email,KeyType=HASH ^
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 ^
  --endpoint-url %ENDPOINT% --region %REGION%

echo Creating Course table...
aws dynamodb create-table --table-name Course ^
  --attribute-definitions AttributeName=courseId,AttributeType=S ^
  --key-schema AttributeName=courseId,KeyType=HASH ^
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 ^
  --endpoint-url %ENDPOINT% --region %REGION%

echo Creating Enrollment table...
aws dynamodb create-table --table-name Enrollment ^
  --attribute-definitions AttributeName=studentId,AttributeType=S AttributeName=courseId,AttributeType=S ^
  --key-schema AttributeName=studentId,KeyType=HASH AttributeName=courseId,KeyType=RANGE ^
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 ^
  --endpoint-url %ENDPOINT% --region %REGION%

timeout /t 2 >nul

echo.
echo ==============================================
echo 🎓 Inserting Sample Courses
echo ==============================================

aws dynamodb put-item --table-name Course --item "{\"courseId\":{\"S\":\"CSE101\"},\"title\":{\"S\":\"Intro to CS\"},\"maxSeats\":{\"N\":\"30\"},\"currentEnrolled\":{\"N\":\"1\"}}" --endpoint-url %ENDPOINT% --region %REGION%
aws dynamodb put-item --table-name Course --item "{\"courseId\":{\"S\":\"CSE303\"},\"title\":{\"S\":\"Artificial Intelligence\"},\"maxSeats\":{\"N\":\"50\"},\"currentEnrolled\":{\"N\":\"1\"}}" --endpoint-url %ENDPOINT% --region %REGION%
aws dynamodb put-item --table-name Course --item "{\"courseId\":{\"S\":\"PHY301\"},\"title\":{\"S\":\"Physics Fundamentals\"},\"maxSeats\":{\"N\":\"40\"},\"currentEnrolled\":{\"N\":\"0\"}}" --endpoint-url %ENDPOINT% --region %REGION%
aws dynamodb put-item --table-name Course --item "{\"courseId\":{\"S\":\"CSE304\"},\"title\":{\"S\":\"Database Systems\"},\"maxSeats\":{\"N\":\"5\"},\"currentEnrolled\":{\"N\":\"0\"}}" --endpoint-url %ENDPOINT% --region %REGION%
aws dynamodb put-item --table-name Course --item "{\"courseId\":{\"S\":\"MTH201\"},\"title\":{\"S\":\"Linear Algebra\"},\"maxSeats\":{\"N\":\"40\"},\"currentEnrolled\":{\"N\":\"1\"}}" --endpoint-url %ENDPOINT% --region %REGION%

echo.
echo ==============================================
echo ✅ Setup Complete!
echo Run this to verify:
echo aws dynamodb scan --table-name Course --endpoint-url %ENDPOINT% --region %REGION% --output table
echo ==============================================
pause
