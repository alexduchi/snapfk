from pathlib import Path

p = Path('device-lab-ci/app/src/main/java/com/devicelab/app/MainActivity.java')
s = p.read_text()

# Keep the microphone fix that was already validated in v4.0.
start = s.index('    private void startMic(){')
end = s.index('    private void stopMic(){', start)
mic = '''    private void startMic(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);
            return;
        }
        if(micRunning)return;
        try{
            final int rate=16000;
            int min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)min=4096;
            audioRecord=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4096));
            if(audioRecord.getState()!=AudioRecord.STATE_INITIALIZED){
                audioRecord.release();
                audioRecord=null;
                Toast.makeText(this,"Micro non initialisable",Toast.LENGTH_LONG).show();
                return;
            }
            micRunning=true;
            audioRecord.startRecording();
            micThread=new Thread(new Runnable(){
                @Override public void run(){
                    short[] buf=new short[1024];
                    while(micRunning&&audioRecord!=null){
                        int n=audioRecord.read(buf,0,buf.length);
                        if(n>0){
                            double sum=0;
                            for(int i=0;i<n;i++){
                                double sample=buf[i]/32768.0;
                                sum+=sample*sample;
                            }
                            double rms=Math.sqrt(sum/n);
                            final double db=rms>0?20*Math.log10(rms):-120;
                            final int meter=(int)Math.max(0,Math.min(100,(db+80)*1.25));
                            ui.post(new Runnable(){
                                @Override public void run(){
                                    if(micValue!=null)micValue.setText(String.format(Locale.ROOT,"%.1f dBFS",db));
                                    if(micMeter!=null)micMeter.setProgress(meter);
                                }
                            });
                        }
                    }
                }
            },"DeviceLabMic");
            micThread.start();
        }catch(Exception e){
            micRunning=false;
            Toast.makeText(this,"Micro indisponible: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
        }
    }
'''
s = s[:start] + mic + s[end:]

# Battery-only stability patch. Standard values still come from ACTION_BATTERY_CHANGED.
start = s.index('    private void buildBatteryPage(){')
end = s.index('    private String batteryStatus(', start)
battery = '''    private void buildBatteryPage(){
        addHeading("Battery","État batterie Android. Les données standard viennent du broadcast système; les mesures avancées sont isolées pour qu’une propriété non supportée ne puisse jamais fermer l’app.");
        batteryGauge=new CircularGaugeView(this);
        content.addView(batteryGauge,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,205)));
        batteryFields=Ui.card(this);
        addCard(batteryFields);
        refreshBatteryPage();
    }

    private void refreshBatteryPage(){
        if(batteryFields==null)return;
        try{
            batteryFields.removeAllViews();
            Intent i=lastBatteryIntent;
            if(i==null){
                batteryFields.addView(Ui.muted(this,"Android n’a pas encore fourni l’état batterie."));
                return;
            }
            int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
            int scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
            float pct=level>=0&&scale>0?level*100f/scale:Float.NaN;
            if(batteryGauge!=null)batteryGauge.setValue(pct,Float.isFinite(pct)?String.format(Locale.ROOT,"%.0f%%",pct):"—");
            int temp=i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
            int volt=i.getIntExtra(BatteryManager.EXTRA_VOLTAGE,Integer.MIN_VALUE);
            int status=i.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
            int health=i.getIntExtra(BatteryManager.EXTRA_HEALTH,-1);
            int plug=i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);
            field(batteryFields,"Niveau",Float.isFinite(pct)?Ui.f(pct,1,"%"):"Indisponible");
            field(batteryFields,"Température",temp!=Integer.MIN_VALUE?Ui.f(temp/10.0,1,"°C"):"Indisponible");
            field(batteryFields,"Tension",volt!=Integer.MIN_VALUE?Ui.f(volt/1000.0,3,"V"):"Indisponible");
            field(batteryFields,"État",batteryStatus(status));
            field(batteryFields,"Santé",batteryHealth(health));
            field(batteryFields,"Source",plugged(plug));
            BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE);
            if(bm!=null){
                field(batteryFields,"Capacité Android",safeBatteryInt(bm,BatteryManager.BATTERY_PROPERTY_CAPACITY,1.0,"%",1));
                field(batteryFields,"Courant instantané",safeBatteryInt(bm,BatteryManager.BATTERY_PROPERTY_CURRENT_NOW,1000.0,"mA",1));
                field(batteryFields,"Courant moyen",safeBatteryInt(bm,BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE,1000.0,"mA",1));
                field(batteryFields,"Charge restante",safeBatteryInt(bm,BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER,1000.0,"mAh",1));
                field(batteryFields,"Énergie restante",safeBatteryEnergy(bm));
            }
        }catch(RuntimeException e){
            batteryFields.removeAllViews();
            batteryFields.addView(Ui.muted(this,"Certaines informations batterie avancées ne sont pas exposées par ce téléphone."));
        }
    }

    private String safeBatteryInt(BatteryManager b,int id,double divisor,String unit,int decimals){
        try{
            int v=b.getIntProperty(id);
            if(v==Integer.MIN_VALUE)return "Indisponible";
            return Ui.f(v/divisor,decimals,unit);
        }catch(RuntimeException e){
            return "Indisponible";
        }
    }

    private String safeBatteryEnergy(BatteryManager b){
        try{
            long v=b.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
            return v==Long.MIN_VALUE?"Indisponible":Ui.f(v/1_000_000_000.0,3,"Wh");
        }catch(RuntimeException e){
            return "Indisponible";
        }
    }
'''
s = s[:start] + battery + s[end:]

# Battery screen refreshes from the system battery broadcast instead of every 180 ms.
s = s.replace(
    'private void refreshVisiblePage(){if("Sensors".equals(page))refreshSensorsUi();else if("Battery".equals(page))refreshBatteryPage();}',
    'private void refreshVisiblePage(){if("Sensors".equals(page))refreshSensorsUi();}'
)
p.write_text(s)

manifest = Path('device-lab-ci/app/src/main/AndroidManifest.xml')
m = manifest.read_text()
m = m.replace('android:versionCode="400" android:versionName="4.0"', 'android:versionCode="401" android:versionName="4.0.1"')
manifest.write_text(m)
