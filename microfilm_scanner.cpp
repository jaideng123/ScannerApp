#include <AccelStepper.h>
#include <QueueArray.h>
#define HALFSTEP 8

// motor pins
#define motorPin1  4     // IN1 on the ULN2003 driver 1
#define motorPin2  5     // IN2 on the ULN2003 driver 1
#define motorPin3  6     // IN3 on the ULN2003 driver 1
#define motorPin4  7     // IN4 on the ULN2003 driver 1

#define motorPin5  8     // IN1 on the ULN2003 driver 2
#define motorPin6  9     // IN2 on the ULN2003 driver 2
#define motorPin7  10    // IN3 on the ULN2003 driver 2
#define motorPin8  11    // IN4 on the ULN2003 driver 2
#define solenoidPinl 3    //output pin of left solenoid
#define solenoidPinr 2    //output pin of right solenoid


// Initialize with pin sequence IN1-IN3-IN2-IN4 for using the AccelStepper with 28BYJ-48
AccelStepper stepper1(HALFSTEP, motorPin1, motorPin3, motorPin2, motorPin4);//Left Motor
AccelStepper stepper2(HALFSTEP, motorPin5, motorPin7, motorPin6, motorPin8);//Right Motor

// variables
int turnSteps = 5; // number of steps to move forward each time
int lineSteps = -5; //number of steps to move backward each time
int stepperSpeed = 1000; //speed of the stepper (steps per second)
int steps1 = 0; // keep track of the step count for motor 1
int steps2 = 0; // keep track of the step count for motor 2
bool leftDisabled = false;
bool rightDisabled = false;
unsigned long previousMillis = 0; // last time update
long interval = 5000; // interval at which to do something (milliseconds)
QueueArray <char> queue; 

void disableGear(char x){
  //Disable Left Gear
  if(x == 'L'){
      leftDisabled = true;
      rightDisabled = false;
      digitalWrite(solenoidPinl, LOW);
      digitalWrite(solenoidPinr, HIGH);
  }
  //Disable Right Gear
  else if(x == 'R'){
      leftDisabled = false;
      rightDisabled = true;
      digitalWrite(solenoidPinr, LOW);
      digitalWrite(solenoidPinl, HIGH);
  }
  else if(x == 'B'){
    leftDisabled = false;
    rightDisabled = false;
    digitalWrite(solenoidPinr, LOW);
    digitalWrite(solenoidPinl, LOW);
  }
//  

  delay(400);
}

void setup() {
  Serial.begin(9600);
  pinMode(solenoidPinl, OUTPUT);           //Sets the pin as an output
  pinMode(solenoidPinr, OUTPUT);           //Sets the pin as an output
  stepper1.setMaxSpeed(2000.0);
  stepper1.move(1);  // I found this necessary
  stepper1.setSpeed(stepperSpeed);
  stepper2.setMaxSpeed(2000.0);
  stepper2.move(1);  // I found this necessary
  stepper2.setSpeed(stepperSpeed);
//   queue.enqueue('1');
//   queue.enqueue('1');
//   queue.enqueue('1');
//   queue.enqueue('3');
//   queue.enqueue('3');
//   queue.enqueue('3');
  

}
void loop() {
  if(Serial.available() > 0)      // Send data only when you receive data:
  {
      int data = Serial.read();        //Read the incoming data & store into data
      Serial.print(data);          //Print Value inside data in Serial monitor
      Serial.print("\n");
      //Clear our queue
      if(data == 'C'){
        while(!queue.isEmpty()){
          queue.dequeue();
        }
      }
      else if(queue.count() < 100){
        queue.enqueue(data);
      }
      
  }
  char current = 0;
  //when we're not working on anything, grab the next instruction
  if(steps1 == 0 && steps2 == 0 && !queue.isEmpty()){
    current = queue.dequeue();
    previousMillis = millis();
    Serial.print(current);          //Print Value inside data in Serial monitor
    Serial.print("\n");
  }
  else if(millis()-previousMillis > interval){
    disableGear('B');
    stepper1.disableOutputs();
    stepper2.disableOutputs();
    previousMillis = millis();
  }
  
  //Move Left Gear Clockwise
  if (current == '1') {
    if(!rightDisabled){
      disableGear('R');
    }
    stepper1.enableOutputs();
    int target = turnSteps;
    stepper1.move(target);
    stepper1.setSpeed(stepperSpeed);
  }
  //Move Left Gear Counter-Clockwise
  else if(current == '2'){
    if(!rightDisabled){
      disableGear('R');
    }
    stepper1.enableOutputs();
    int target = lineSteps;
    stepper1.move(target);
    stepper1.setSpeed(stepperSpeed);
  }
  //Move Right Gear Clockwise
  if (current == '3') {
    if(!leftDisabled){
      disableGear('L');
    }
    stepper2.enableOutputs();
    int target = turnSteps;
    stepper2.move(target);
    stepper2.setSpeed(stepperSpeed);
  }
  //Move Right Gear Counter-Clockwise
  else if(current == '4'){
    if(!leftDisabled){
      disableGear('L');
    }
    stepper2.enableOutputs();
    int target = lineSteps;
    stepper2.move(target);
    stepper2.setSpeed(stepperSpeed);
  }
  //Disable Left Or Right Gear
  else if(current == 'L' || current == 'R'){
    disableGear(current);
  }

  steps1 = stepper1.distanceToGo();
  steps2 = stepper2.distanceToGo();

  stepper1.runSpeedToPosition();
  stepper2.runSpeedToPosition();
}
