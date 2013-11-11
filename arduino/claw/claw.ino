#include <Wire.h>
#include <AccelStepper.h>
#include <Servo.h> 

// Define a stepper and the pins it will use
AccelStepper sx(1, 5, 4);
AccelStepper sy(1, 13, 12);
AccelStepper sz(1, 16, 15);
AccelStepper steppers[] = {sx, sy, sz};

Servo claw;
// see loopClawDrop for status handling.
int claw_status = 0;
int clawpin = 17;
int claw_open = 20;
int claw_closed = 180;
int claw_drop_distance = 500;
int claw_close_time = 10000;
int claw_read_value = 0;
boolean claw_read_latch = false;
int val;


byte buffer[32];
unsigned int a1, a2, comm;
byte moveX, moveY, moveZ;
int pos;

// Stepper index, Moving status, sleep pin, low limit pin index, high limit pin index
int xstep[] = {0, 1, 3, 0, 1};
int ystep[] = {1, 1, 11, 2, 3};
int zstep[] = {2, 1, 14};

// motor constants
int enmaster = 10;
int i = 0;

int PKTLEN = 6;

int enable;

// {x lower, x upper, y lower, y upper}
int limits = 4;
int limit_pins[] = {9, 8, 7, 6};
boolean limits_hit[] = {false, false, false, false};
long bounce_times[] = {0,0,0,0};
long debounceDelay = 150;
boolean limits_bounce[] = {false, false, false, false};
boolean initialized = false;

// Calibrating state - 0 none, 1 runninng, 2 centering
int calibrating = 0;

int calibrate_speed = 200;
int max_x = 100;
int max_y = 100;

void setupStepper(int* stepper) {
  stepper[1] = 0;
  pinMode(stepper[2], OUTPUT);
  digitalWrite(stepper[2],1);
  steppers[stepper[0]].setMaxSpeed(1500);
  steppers[stepper[0]].setAcceleration(100);
}

void moveStepper(int* stepper, int pos) {
  digitalWrite(stepper[2],0);
  stepper[1] = 1;
  steppers[stepper[0]].moveTo(pos);
}

void stepperSpeed(int* stepper, int maxSpeed, int accel) {
  steppers[stepper[0]].setMaxSpeed(maxSpeed);
  steppers[stepper[0]].setAcceleration(accel);
}

void stopStepper(int* stepper) {
  digitalWrite(stepper[2],1);
  stepper[1] = 0;
  AccelStepper s = steppers[stepper[0]];
  s.moveTo(s.currentPosition());
}

void checkStepperDistance(int* stepper) {
  if (stepper[1] > 0) { 
    if (steppers[stepper[0]].distanceToGo() == 0) {
      stopStepper(stepper);
    }
  }
}

void openClaw() {
  claw.write(claw_open);
}

void closeClaw() {
  claw.write(claw_closed);
}

void resetClaw() {
  calibrating = 2;
  moveStepper(xstep, max_x/2);
  moveStepper(ystep, max_y/2);
  moveStepper(zstep, 0);
  openClaw();
}

void setup() {
  Wire.begin(4);                // join i2c bus with address #4
  Wire.onReceive(receiveEvent); // register event
  Serial.begin(57600);         // start serial for output  
}

void initializeSteppers() {
  // clear buffer
  pos = 0;
  for (int i = 0; i < sizeof(buffer); i++) {
    buffer[i] = 0;
  }

  for (int i = 0; i < limits; i++) {
    Serial.print("Setting ");
    Serial.print(limit_pins[i]);
    Serial.println(" as input pin.");
    pinMode(limit_pins[i], INPUT);
    limits_bounce[i] = false;
  }

// configure motors  
  pinMode(enmaster, INPUT);
  
  setupStepper(xstep);
  setupStepper(ystep);
  setupStepper(zstep);

  claw.attach(clawpin);
  initialized = true;
}

void checkdistance() {
  checkStepperDistance(xstep);
  checkStepperDistance(ystep);
  checkStepperDistance(zstep);
}

void startCalibrate() {
  Serial.println("Reset command");
  for(int i= 0; i < limits;i++) {
    limits_hit[i] = false;
  }
  calibrating = 1;
  closeClaw();
}

void moveCommand(int x, int y) {
  Serial.println("Motor command, enabling SX and SY");
  
  moveStepper(xstep, x);
  moveStepper(ystep, y);
}

void moveClawCommand(int z, int clawpos) {
  Serial.println("Claw movement  command, manual");
  
  moveStepper(zstep, z);

  claw.write(clawpos);
}

void dropClaw() {
  claw_status = 1;
}

void setSpeed(int maxSpeed, int accel) {
  stepperSpeed(xstep, maxSpeed, accel);
  stepperSpeed(ystep, maxSpeed, accel);
}

void writeResponse(int command, int arg1, int arg2) {
  Wire.write((command >> 8) & 0xFF); 
  Wire.write(command & 0xFF); 
  Wire.write((arg1 >> 8) & 0xFF);
  Wire.write(arg1 & 0xFF); 
  Wire.write((arg2 >> 8) & 0xFF);
  Wire.write(arg2 & 0xFF);
}

void processPacket() {
  comm = (buffer[0] << 8) + buffer[1];
  a1   = (buffer[2] << 8) + buffer[3];
  a2   = (buffer[4] << 8) + buffer[5];
 
//   debug to serial port        
//  Serial.print("Packet received (comm, a1, a2)");
        
  Serial.println(comm);
  Serial.println(a1);
  Serial.println(a2);

  if (!initialized) {
    if (comm == 6) {
      initializeSteppers();
    } else {
      Serial.println("System not initialized, please send 6.");
    }
    
    return;
  }

  if (calibrating > 0) {
    Serial.println("Chill. Busy calibrating");
    return;
  }

  if (claw_status > 0) {
    Serial.println("Claw busy dropping.. hang on.");
  }

  if (comm == 1) {
    startCalibrate();
  } else if (comm == 2) {
    moveCommand(a1, a2);
  } else if (comm == 3) {
    dropClaw();
  } else if (comm == 4) {
    setSpeed(a1, a2);
  } else if (comm == 5) {
    moveClawCommand(a1, a2);
  } else if (comm == 6) {
    // Initialize - already done, so do nothing.
  } else if (comm == 7) {
    claw_read_latch = true;
  } else if (comm == 10) { // Status
    writeResponse(10, calibrating, claw_status);
    writeResponse(11, sx.currentPosition(), sy.currentPosition());
    writeResponse(12, sz.currentPosition(), claw.read());
    writeResponse(13, max_x, max_y);
    writeResponse(14, claw_drop_distance, 0);
  }
}

// function that executes whenever data is received from master
// this function is registered as an event, see setup()
void receiveEvent(int howMany) {
  pos = 0;
  // loop through all but the last
  while(1 <= Wire.available()) {
    byte c = Wire.read(); // receive byte as a character
    if (pos < PKTLEN) { 
      buffer[pos] = c;
      pos++;
    }
    
   if (pos == PKTLEN) {
      Serial.println("Packet");
      processPacket();
      pos = 0;
    }
  }
}

void checkLimits(int* stepper) {
  int lowlimit = stepper[3];
  int uplimit = stepper[4];
  AccelStepper s = steppers[stepper[0]];

  // Min
  if ((digitalRead(limit_pins[lowlimit]) == HIGH)) {
    if (!limits_bounce[lowlimit]) {
      Serial.print(limit_pins[lowlimit]);
      Serial.println(" - min limit <");
      if (s.targetPosition() < s.currentPosition()) {
        s.moveTo(s.currentPosition());
      }
      bounce_times[lowlimit] = millis();
      s.setCurrentPosition(0);
      s.moveTo(0);
      limits_bounce[lowlimit] = true;
    }
  } else {
    if ((millis() - bounce_times[lowlimit]) > debounceDelay) {
      limits_bounce[lowlimit] = false;
    }
  }

  // Max
  if (digitalRead(limit_pins[uplimit]) == HIGH) {
    if (!limits_bounce[uplimit]) {
      Serial.print(limit_pins[uplimit]);
      Serial.println(" - max limit >");
      if (s.targetPosition() > s.currentPosition()) {
        s.moveTo(s.currentPosition());
      }
      bounce_times[uplimit] = millis();
      limits_bounce[uplimit] = true;
      
      // If the low limit and upper limit are simultaneously down, start calibration
      if (limits_bounce[lowlimit]) {
        Serial.println("Trigger manual calibration");
        calibrating = 9;
      }
    }
  } else {
    if ((millis() - bounce_times[uplimit]) > debounceDelay) {
      limits_bounce[uplimit] = false;
    }
  }
}

void runCalibrate(int* stepper) {
  int lowlimit = stepper[3];
  int uplimit = stepper[4];
  AccelStepper s = steppers[stepper[0]];

  if (!limits_hit[lowlimit]) {
    if (limits_bounce[lowlimit]) {
      limits_hit[lowlimit] = true;
    } else {
      moveStepper(stepper, s.currentPosition()-100);
      s.setSpeed(calibrate_speed);
      s.runSpeedToPosition();
    }
  } else if (!limits_hit[uplimit]) {
    if (limits_bounce[uplimit]) {
      limits_hit[uplimit] = true;
    } else {
      moveStepper(stepper, s.currentPosition()+100);
      s.setSpeed(calibrate_speed);
      s.runSpeedToPosition();
    }
  } else {
    stopStepper(stepper);
  }
}


void loopCalibrateCenter() {
  if (xstep[1] == 0 && ystep[1] == 0) {
    Serial.println("Finished Centering Calibrate");
    calibrating = 0;
    openClaw();
  }
}

void loopCalibrate() {
  if (calibrating == 9) {
    boolean alloff = true;
    for (int i = 0; i < limits; i++) {
      if (digitalRead(limit_pins[i]) == HIGH) {
        alloff = false;
      }
    }
    
    if (alloff) {
      Serial.println("Starting manual calibration");
      startCalibrate();
    }
    return;
  }
  if (calibrating == 2) {
    loopCalibrateCenter();
    return;
  }

  boolean allhit = true;
  for (int i = 0; i < limits; i++) {
    if (!limits_hit[i]) {
      allhit = false;
    }
  }


  if (allhit) {
    max_x = steppers[xstep[0]].currentPosition();
    max_y = steppers[ystep[0]].currentPosition();
    resetClaw();
    Serial.println("Finished Calibrating, moving to center");
    Serial.print("MaxX: ");
    Serial.print(max_x);
    Serial.print("MaxY: ");
    Serial.print(max_y);
    Serial.println("");
    return;
  } else {
    runCalibrate(xstep);
    runCalibrate(ystep);
  }
}

void loopClawDrop() {
  // 0 = inactive, 1 = starting, 3 = dropping, 5 = grabbing, 7 = lifting, 9 = reading, 11 = resetting
  switch (claw_status) {
  case 1: { // starting
    Serial.println("Dropping CLAW!");
    moveStepper(zstep, claw_drop_distance);
    claw_status = 3;
    break;
  }
  case 3: { // Dropping
    if (zs.distanceToGo() == 0) {
      claw_status = 5;
      Serial.println("Grabbing");
    }
    break;
  }
  case 5: { // Grabbing
    closeClaw();
    delay(claw_close_time);
    moveStepper(zstep, 0);
    claw_status = 7
    Serial.println("Lifting");
    break;
  }
  case 7: { // Lifting
    if (zs.distanceToGo() == 0) {
      claw_status = 9;
      claw_read_latch = false;
      Serial.println("Reading");
    }
    break;
  }
  case 9: { // Reading
    if (claw_read_latch) {
      resetClaw();
      claw_status = 0;
      Serial.println("Resetting");
    }
  }
}

void loop() {
  // send data only when you receive data:
  if (Serial.available() > 0) {
    // read the incoming byte:
    byte incomingByte = Serial.read();
    // say what you got:
    Serial.print("Serial received: ");
    Serial.println(incomingByte, DEC);
  
    if (incomingByte == 105) { // i
      Serial.println("Initializing Steppers");
      initializeSteppers();
    } else if (incomingByte == 99) { // c
      Serial.println("Calibrating");
      startCalibrate();
    } else if (incomingByte == 100) {
      Serial.println("Incoming Claw Drop!");
      dropClaw();
    }
  }
  
       
  if (initialized) {
    checkdistance();
    checkLimits(xstep);
    checkLimits(ystep);
    if (calibrating > 0) {
      loopCalibrate();
      return;
    }

    steppers[xstep[0]].run();
    steppers[ystep[0]].run();
    steppers[zstep[0]].run();

    if (claw_status > 0) {
      loopClawDrop();
    }
  }
}

