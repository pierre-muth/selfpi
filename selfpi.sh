#sleep 2
#/home/pi/selfpi/raspicam/raspitimescan -w 576 -h 576 -p 0,0,1024,1024 -ev +5.0 -co 10 -fps 90 -drc med -ex sports -md 6 -n -vf -t 3000 -tf /home/pi/selfpi/raspicam/gradien.png -o test.jpg
sleep 2
cd /home/pi/selfpi/jar
sudo java -jar selfpi.jar

