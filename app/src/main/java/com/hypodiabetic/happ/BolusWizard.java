
package com.hypodiabetic.happ;

import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.hypodiabetic.happ.Objects.Profile;
import com.hypodiabetic.happ.Objects.Treatments;
import com.hypodiabetic.happ.integration.nightscout.cob;
import com.hypodiabetic.happ.Objects.Bg;
import com.hypodiabetic.happ.integration.openaps.IOB;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BolusWizard {


    //main HAPP function
    public static JSONObject bw (Double carbs){

        Date dateNow = new Date();
        Profile profile = new Profile(dateNow);
        JSONObject iobNow       = IOB.iobTotal(profile, dateNow);
        JSONObject cobNow       = Treatments.getCOB(profile, dateNow);
        String bgCorrection;

        Bg bg = Bg.last();
        Double lastBG = 0D;
        if (bg != null) lastBG = bg.sgv_double();

        Double cob, iob;
        //eventualBG  = openAPSNow.optDouble("eventualBG",0D);
        //snoozeBG    = openAPSNow.optDouble("snoozeBG",0D);
        cob         = cobNow.optDouble("cob",0D);
        iob         = iobNow.optDouble("iob",0D);


        Double insulin_correction_bg;
        String insulin_correction_bg_maths;
        String suggested_bolus_maths;
        Double suggested_bolus;
        String suggested_correction_maths;
        Double suggested_correction;
        Double net_correction_biob;
        String net_biob_correction_maths;

        //Net IOB after current carbs taken into consideration
        if (iob < 0){
            net_correction_biob         =   (cob / profile.carbRatio) + iob;
            if (net_correction_biob.isNaN() || net_correction_biob.isInfinite()) net_correction_biob = 0D;
            net_biob_correction_maths   = "(COB(" + cob + ") / Carb Ratio(" + profile.carbRatio + "g)) + IOB(" + tools.formatDisplayInsulin(iob,2) + ") = " + tools.formatDisplayInsulin(net_correction_biob,2);
        } else {
            net_correction_biob         =   (cob / profile.carbRatio) - iob;
            if (net_correction_biob.isNaN() || net_correction_biob.isInfinite()) net_correction_biob = 0D;

            //Ignore positive correction if BG is low
            if (lastBG <= profile.min_bg && net_correction_biob > 0) {
                net_biob_correction_maths   = "Low BG: Suggested Corr " + tools.formatDisplayInsulin(net_correction_biob,2) + " Setting to 0";
                net_correction_biob = 0D;
            } else {
                net_biob_correction_maths   = "(COB(" + cob + ") / Carb Ratio(" + profile.carbRatio + "g)) - IOB(" + tools.formatDisplayInsulin(iob,2) + ") = " + tools.formatDisplayInsulin(net_correction_biob,2);
            }
        }

        //Insulin required for carbs about to be consumed
        Double insulin_correction_carbs         = carbs / profile.carbRatio;
        if (insulin_correction_carbs.isNaN() || insulin_correction_carbs.isInfinite()) insulin_correction_carbs = 0D;
        String insulin_correction_carbs_maths   = "Carbs(" + carbs + "g) / Carb Ratio(" + profile.carbRatio + "g) = " + tools.formatDisplayInsulin(insulin_correction_carbs,2);

        //Insulin required for BG correction
        if (lastBG >= profile.max_bg) {                                                              //True HIGH
            insulin_correction_bg = (lastBG - profile.max_bg) / profile.isf;
            bgCorrection = "High";
            insulin_correction_bg_maths = "BG(" + lastBG + ") - (Max BG(" + profile.max_bg + ") / ISF(" + profile.isf + ")) = " + tools.formatDisplayInsulin(insulin_correction_bg,2);

        } else if (lastBG <= (profile.min_bg-30)){                                                  //Critical LOW
            insulin_correction_bg       = (lastBG - profile.target_bg) / profile.isf;
            bgCorrection                = "Critical Low";
            if(insulin_correction_bg > 0) {
                insulin_correction_bg_maths = "Suggestion " + insulin_correction_bg + "U, Blood Sugars below " + (profile.min_bg-30) + ". Setting to 0.";
                insulin_correction_bg   = 0D;
            } else {
                insulin_correction_bg_maths = "(BG(" + lastBG + ") - Target BG(" + profile.target_bg + ") / ISF(" + profile.isf + ") = " + tools.formatDisplayInsulin(insulin_correction_bg,2);
            }
            
        } else if (lastBG <= profile.min_bg){                                                       //True LOW
            insulin_correction_bg       = (lastBG - profile.target_bg) / profile.isf;
            bgCorrection                = "Low";
            insulin_correction_bg_maths = "(BG(" + lastBG + ") - Target BG(" + profile.target_bg + ") / ISF(" + profile.isf + ") = " + tools.formatDisplayInsulin(insulin_correction_bg,2);
        } else {                                                                                    //IN RANGE
            insulin_correction_bg       = 0D;
            bgCorrection                = "Within Target";
            insulin_correction_bg_maths = "NA - BG within Target";
        }
        if (insulin_correction_bg.isNaN() || insulin_correction_bg.isInfinite()) insulin_correction_bg = 0D;
        suggested_correction        = insulin_correction_bg + net_correction_biob;
        suggested_correction_maths  = "BG Corr(" + tools.formatDisplayInsulin(insulin_correction_bg,2) + ") - Net Bolus(" + tools.formatDisplayInsulin(net_correction_biob,2) + ") = " + tools.formatDisplayInsulin(suggested_correction,2);


        //if (suggested_correction < 0) {
        //    suggested_bolus = insulin_correction_carbs + suggested_correction;
        //    suggested_bolus_maths = "Carb Corr(" + tools.formatDisplayInsulin(insulin_correction_carbs, 2) + ") + " + "Neg Corr(" + tools.formatDisplayInsulin(suggested_correction,2) + ") = " + tools.formatDisplayInsulin(suggested_bolus,2);
        //    suggested_correction = 0D;
        //} else {
            suggested_bolus = insulin_correction_carbs;
            suggested_bolus_maths = "Carb Corr(" + tools.formatDisplayInsulin(insulin_correction_carbs, 2) + ") = " + tools.formatDisplayInsulin(suggested_bolus,2);
        //}


        JSONObject reply = new JSONObject();
        try {
            reply.put("isf",profile.isf);
            reply.put("iob",iob);
            reply.put("cob",cob);
            reply.put("carbRatio",profile.carbRatio);
            //reply.put("bolusiob",biob);
            //reply.put("eventualBG",eventualBG);
            //reply.put("snoozeBG",snoozeBG);
            reply.put("max_bg",profile.max_bg);
            reply.put("target_bg",profile.target_bg);
            reply.put("bgCorrection",bgCorrection);
            if (net_correction_biob > 0){
                reply.put("net_biob",                   "+" + tools.formatDisplayInsulin(net_correction_biob,1));
            } else {
                reply.put("net_biob",                   tools.formatDisplayInsulin(net_correction_biob,1));
            }
            reply.put("net_biob_maths",                 net_biob_correction_maths);
            if (insulin_correction_carbs > 0){
                reply.put("insulin_correction_carbs",   "+" + tools.formatDisplayInsulin(insulin_correction_carbs,1));
            } else {
                reply.put("insulin_correction_carbs",   tools.formatDisplayInsulin(insulin_correction_carbs,1));
            }
            reply.put("insulin_correction_carbs_maths", insulin_correction_carbs_maths);
            if (insulin_correction_bg > 0){
                reply.put("insulin_correction_bg",      "+" + tools.formatDisplayInsulin(insulin_correction_bg,1));
            } else {
                reply.put("insulin_correction_bg",      tools.formatDisplayInsulin(insulin_correction_bg,1));
            }
            reply.put("insulin_correction_bg_maths",    insulin_correction_bg_maths);
            if (suggested_bolus < 0) suggested_bolus=0D;
            reply.put("suggested_bolus",                suggested_bolus);
            reply.put("suggested_bolus_maths",          suggested_bolus_maths);
            reply.put("suggested_correction",           suggested_correction);
            reply.put("suggested_correction_maths",     suggested_correction_maths);
        } catch (JSONException e) {
            Crashlytics.logException(e);
        }
        Log.d("DEBUG", "bw: resut" + reply.toString());
        return reply;

    }






    //main NS functiuon
    public static JSONObject run_NS_BW() {

        Date dateNow = new Date();
        Profile profile = new Profile(dateNow);
        List treatments = Treatments.latestTreatments(20, "Insulin");

        JSONObject bwp = bwp_calc(treatments, profile, dateNow);
        JSONObject reply = pushInfo(bwp, profile);


        List cobtreatments = Treatments.latestTreatments(20, null);
        Collections.reverse(cobtreatments);
        try {
            reply.put("cob", cob.cobTotal(cobtreatments, profile, dateNow).getDouble("display"));
        } catch (JSONException e) {
            Crashlytics.logException(e);
        }

        return bwp;

    }



    public static JSONObject bwp_calc(List treatments, Profile profile, Date dateNow) {

        JSONObject results = new JSONObject();
        try {
            results.put("effect",0);
            results.put("outcome",0);
            results.put("bolusEstimate",0.0);
        } catch (JSONException e) {
            Crashlytics.logException(e);
        }

        Bg scaled = Bg.last();
        Double results_scaledSGV;
        if (scaled == null) {
            return results;                                                                         //exit, as no last BG
            //results_scaledSGV = scaled.sgv_double();
        } else {
            results_scaledSGV = 0D;
        }

        //var errors = checkMissingInfo(sbx);

        //if (errors && errors.length > 0) {
        //    results.errors = errors;
        //    return results;
        //}

        Double iobValue=0D;
        try {
            iobValue = IOB.iobTotal(profile, dateNow).getDouble("bolusiob");
            //iobValue = iob.iobTotal(treatments, profile, dateNow).getDouble("bolusiob");
        } catch (JSONException e) {
            //Toast.makeText(ApplicationContextProvider.getContext(), "Error getting IOB for bwp_calc", Toast.LENGTH_LONG).show();
            Crashlytics.logException(e);
        }

        Double results_effect = iobValue * profile.isf;
        Double results_outcome = scaled.sgv_double() - results_effect;
        Double delta;

        Double target_high = profile.max_bg;
        Double sens = profile.isf;

        Double results_bolusEstimate = 0D;
        Double results_aimTarget=0D;
        String results_aimTargetString="";

        if (results_outcome > target_high) {
            delta = results_outcome - target_high;
            results_bolusEstimate = delta / sens;
            results_aimTarget = target_high;
            results_aimTargetString = "above high";
        }

        Double target_low = profile.min_bg;

        if (results_outcome < target_low) {
            delta = Math.abs(results_outcome - target_low);
            results_bolusEstimate = delta / sens * -1;
            results_aimTarget = target_low;
            results_aimTargetString = "below low";
        }

        if (results_bolusEstimate != 0 && profile.current_basal != 0) {
            // Basal profile exists, calculate % change
            Double basal = profile.current_basal;

            Double thirtyMinAdjustment  = (double) Math.round((basal/2 + results_bolusEstimate) / (basal / 2) * 100);
            Double oneHourAdjustment    = (double) Math.round((basal + results_bolusEstimate) / basal * 100);

            // TODO: 02/09/2015 this should be in a sub JSON object called tempBasalAdjustment
            try {
                results.put("tempBasalAdjustment-thirtymin",thirtyMinAdjustment);
                results.put("tempBasalAdjustment-onehour",oneHourAdjustment);
            } catch (JSONException e) {
                Crashlytics.logException(e);
            }
        }

        try {
            results.put("effect",results_effect);
            results.put("outcome",results_outcome);
            results.put("bolusEstimate",results_bolusEstimate);
            results.put("aimTarget",results_aimTarget);
            results.put("aimTargetString",results_aimTargetString);
            results.put("scaledSGV",results_scaledSGV);
            results.put("iob",iobValue);

            results.put("bolusEstimateDisplay",     tools.round(results_bolusEstimate, 2));         //String.format(Locale.ENGLISH, "%.2f",results_bolusEstimate));
            results.put("outcomeDisplay",           tools.round(results_outcome, 2));               //String.format(Locale.ENGLISH, "%.2f", results_outcome));
            results.put("displayIOB",               tools.round(iobValue, 2));                      //String.format(Locale.ENGLISH, "%.2f", iobValue));
            results.put("effectDisplay",            tools.round(results_effect, 2));                //String.format(Locale.ENGLISH, "%.2f", results_effect));
            results.put("displayLine", "BWP: " +    tools.formatDisplayInsulin(results_bolusEstimate, 2)); //String.format(Locale.ENGLISH, "%.2f",results_bolusEstimate) + "U");
        } catch (JSONException e) {
            Crashlytics.logException(e);
        }

        return results;
    }

    public static JSONObject pushInfo(JSONObject prop, Profile profile) {
        //if (prop && prop.errors) {
        //    info.push({label: 'Notice', value: 'required info missing'});
        //    _.forEach(prop.errors, function pushError (error) {
        //        info.push({label: '  • ', value: error});
        //    });
        //} else if (prop) {


        JSONObject results = new JSONObject();
        try {
            results.put("BOLUS Insulin on Board", prop.optString("displayIOB", "0") + "U");
            results.put("Sensitivity", "-" + profile.isf + "U");
            results.put("Expected effect", prop.optString("displayIOB","0") + " x -" + profile.isf + "= -" + prop.optString("effectDisplay", "0") );
            results.put("Expected outcome", prop.optString("scaledSGV", "0") + "-" + prop.optString("effectDisplay", "0") + " = " + prop.optString("outcomeDisplay", "0"));

            // TODO: 02/09/2015 these items should be put at the top of the JSON object, poss in reverse order
            if (prop.optDouble("bolusEstimate", 0D) < 0) {
                //info.unshift({label: '---------', value: ''});
                Double carbEquivalent = Math.ceil(Math.abs(profile.carbRatio * prop.optDouble("bolusEstimate", 0D)));
                results.put("Carb Equivalent", prop.optString("bolusEstimateDisplay", "0") + "U * " + profile.carbRatio + " = " + carbEquivalent + "g");
                results.put("Current Carb Ratio", "1U for " + profile.carbRatio + "g");
                results.put("-BWP", prop.optString("bolusEstimateDisplay", "0") + "U, maybe covered by carbs?");
            }

        } catch (JSONException e) {
            Crashlytics.logException(e);
        }

        return results;

        //} else {
        //    info.push({label: 'Notice', value: 'required info missing'});
        //}

        // TODO: 02/09/2015 this function appears to give bolus suggestions, maybe intresting to compare with OpenAPS
        //pushTempBasalAdjustments(prop, info, sbx);
    }

}