package com.hypodiabetic.happ.Objects;

import com.crashlytics.android.Crashlytics;
import com.hypodiabetic.happ.Constants;
import com.hypodiabetic.happ.tools;

import java.util.Date;
import java.util.StringTokenizer;

/**
 * Created by Tim on 16/02/2016.
 * Pump object provides point in time info of users pump
 */
public class Pump {

    public String   name;                           //name of pump
    public Integer  basal_mode;                     //Basal adjustment mode
    public Integer  min_low_basal_duration;         //low basal duration supported
    public Integer  min_high_basal_duration;        //low basal duration supported
    public Double   default_basal_rate;             //What is the current default rate
    public Boolean  temp_basal_active=false;        //Is a temp basal active
    public Double   temp_basal_rate;                //Current temp basal rate
    public Integer  temp_basal_percent;             //Current temp basal percent
    public Integer  temp_basal_duration;            //Temp duration in Mins
    public Long     temp_basal_duration_left;       //Mins left of this Temp Basal

    private Profile profile;
    private TempBasal tempBasal;

    private static final int ABSOLUTE               =  1;       //Absolute (U/hr)
    private static final int PERCENT                =  2;       //Percent of Basal
    private static final int BASAL_PLUS_PERCENT     =  3;       //hourly basal rate plus TBR percentage

    public Pump(Date profile_date){

        profile             =   new Profile(profile_date);
        tempBasal           =   TempBasal.last();
        name                =   profile.pump_name;
        default_basal_rate  =   profile.current_basal;

        switch (name){
            case "roche_combo":
            case "medtronic_percent":
                basal_mode              =   BASAL_PLUS_PERCENT;
                min_low_basal_duration  =   30;
                min_high_basal_duration =   30;
                break;
            case "dana_r":
                basal_mode              =   BASAL_PLUS_PERCENT;
                min_low_basal_duration  =   60;
                min_high_basal_duration =   30;
                break;
            case "medtronic_absolute":
                basal_mode              =   ABSOLUTE;
                min_low_basal_duration  =   30;
                min_high_basal_duration =   30;
                break;
            case "animas":
            case "omnipod":
                basal_mode              =   PERCENT;
                min_low_basal_duration  =   30;
                min_high_basal_duration =   30;
                break;
            case "tslim":
                basal_mode              =   PERCENT;
                min_low_basal_duration  =   0;
                min_high_basal_duration =   0;
                break;
        }

        temp_basal_active   =   tempBasal.isactive(new Date());
        if (temp_basal_active){
            temp_basal_rate             =   tempBasal.rate;
            temp_basal_percent          =   getBasalPercent();
            temp_basal_duration         =   tempBasal.duration;
            temp_basal_duration_left    =   tempBasal.durationLeft();
        }
    }

    public Double checkSuggestedRate(Double rate){
        switch (name) {
            case "omnipod":
                //limited to double current basal
                if (rate > (2 * default_basal_rate)) {
                    return 2 * default_basal_rate;
                } else {
                    return rate;
                }
            case "tslim":
                //limited to 250% current basal
                if (rate > (2.5 * default_basal_rate)) {
                    return 2.5 * default_basal_rate;
                } else {
                    return rate;
                }
            default:
                return rate;
        }
    }

    public int getSupportedDuration(Double rate){
        if (rate > default_basal_rate){
            return min_high_basal_duration;
        } else {
            return min_low_basal_duration;
        }
    }

    public void setNewTempBasal(APSResult apsResult, TempBasal tempBasal){
        temp_basal_active   =   true;
        if (apsResult != null){
            temp_basal_rate             =   apsResult.rate;
            temp_basal_duration         =   apsResult.duration;
            temp_basal_duration_left    =   apsResult.duration.longValue();
            if (apsResult.checkIsCancelRequest()) temp_basal_active   =   false;
        } else {
            temp_basal_rate             =   tempBasal.rate;
            temp_basal_duration         =   tempBasal.duration;
            temp_basal_duration_left    =   tempBasal.durationLeft();
            if (tempBasal.checkIsCancelRequest()) temp_basal_active   =   false;
        }
        temp_basal_percent  =   getBasalPercent();
    }

    public String displayCurrentBasal(boolean small){
        if (basal_mode == null) return "Could not detect basal mode";
        String msg="";
        if (small) {
            switch (basal_mode) {
                case ABSOLUTE:
                    msg = tools.formatDisplayBasal(activeRate(), false);
                    break;
                case PERCENT:
                    msg = calcPercentOfBasal() + "%";
                    break;
                case BASAL_PLUS_PERCENT:
                    msg = calcBasalPlusPercent() + "%";
                    break;
            }
        } else {
            switch (basal_mode) {
                case ABSOLUTE:
                    msg = tools.formatDisplayBasal(activeRate(), false);
                    break;
                case PERCENT:
                    msg = calcPercentOfBasal() + "% (" + tools.formatDisplayBasal(activeRate(), false) + ")";
                    break;
                case BASAL_PLUS_PERCENT:
                    msg = calcBasalPlusPercent() + "% (" + tools.formatDisplayBasal(activeRate(), false) + ")";
                    break;
            }
        }

        if (msg.equals("")){
            Crashlytics.log(1,"APSService","Could not get displayCurrentBasal: " + basal_mode + " " + name);
            return "error";
        } else {
            if (temp_basal_active) msg = msg + " TBR";
            return msg;
        }
    }

    public String displayTempBasalMinsLeft(){
        if (temp_basal_active){
            if (temp_basal_duration_left > 1){
                return temp_basal_duration_left + " mins left";
            } else {
                return temp_basal_duration_left + " min left";
            }
        } else {
            return "";
        }
    }

    public String displayBasalDesc(boolean small){
        if (small) {
            if (temp_basal_active) {
                if (temp_basal_rate > default_basal_rate) {
                    return Constants.ARROW_SINGLE_UP;
                } else {
                    return Constants.ARROW_SINGLE_DOWN;
                }
            } else {
                return "";
            }
        } else {
            if (temp_basal_active) {
                if (temp_basal_rate > default_basal_rate) {
                    return "High TBR";
                } else {
                    return "Low TBR";
                }
            } else {
                return "Default Basal";
            }
        }
    }

    private int getBasalPercent(){
        if (basal_mode == null) return 0;
        switch (basal_mode){
            case ABSOLUTE:
                return 0;
            case PERCENT:
                return calcPercentOfBasal();
            case BASAL_PLUS_PERCENT:
                return calcBasalPlusPercent();
        }
        Crashlytics.log(1,"APSService","Could not get getSuggestedBasalPercent: " + basal_mode + " " + name);
        return 0;
    }

    public Double activeRate(){
        if (temp_basal_active){
            return temp_basal_rate;
        } else {
            return default_basal_rate;
        }
    }

    private int calcPercentOfBasal(){
        //Change = Suggested TBR - Current Basal
        //% Change = Change / Current Basal * 100
        //Examples:
        //Current Basal: 1u u/hr
        //Low TBR 0.5 u/hr suggested = -50%
        //High TBR 1.5 u/hr suggested = 50%
        if (activeRate() <=0){
            return -100;
        } else {
            Double ratePercent = (activeRate() - profile.current_basal);
            ratePercent = (ratePercent / profile.current_basal) * 100;

            switch (name){
                case "omnipod":
                    //cap at max 100% and round to closet 5
                    if (ratePercent >= 100) {
                        return 100;
                    } else {
                        ratePercent = (double) Math.round(ratePercent / 5) * 5; //round to closest 5
                        return ratePercent.intValue();
                    }
                default:
                    return ratePercent.intValue();
            }
        }
    }
    private int calcBasalPlusPercent(){
        Double ratePercent = (activeRate() / profile.current_basal) * 100;
        ratePercent = (double) Math.round(ratePercent / 10) * 10; //round to closest 10
        return ratePercent.intValue();
    }

    private String displayBasalMode(){
        if (basal_mode == null) return "Could not detect basal mode";
        switch (basal_mode){
            case ABSOLUTE:
                return "Absolute (U/hr)";
            case PERCENT:
                return "Percent of Basal";
            case BASAL_PLUS_PERCENT:
                return "hourly basal rate plus TBR percentage";
            default:
                return "cannot get basal mode: this is not good";
        }
    }

    @Override
    public String toString(){
        return  "name: " + name + "\n" +
                " basal_mode:" + displayBasalMode() + "\n" +
                " min_low_basal_duration:" + min_low_basal_duration + "\n" +
                " min_high_basal_duration:" + min_high_basal_duration + "\n" +
                " default_basal_rate:" + default_basal_rate + "\n" +
                " temp_basal_active:" + temp_basal_active + "\n" +
                " temp_basal_rate:" + temp_basal_rate + "\n" +
                " temp_basal_percent:" + temp_basal_percent + "\n" +
                " temp_basal_duration:" + temp_basal_duration + "\n" +
                " temp_basal_duration_left:" + temp_basal_duration_left;
    }
}
