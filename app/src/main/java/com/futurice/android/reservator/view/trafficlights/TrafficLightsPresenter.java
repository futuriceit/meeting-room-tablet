package com.futurice.android.reservator.view.trafficlights;

import android.app.Activity;
import android.content.res.Resources;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.os.Handler;

import com.futurice.android.reservator.R;
import com.futurice.android.reservator.common.Helpers;
import com.futurice.android.reservator.common.PreferenceManager;
import com.futurice.android.reservator.model.DateTime;
import com.futurice.android.reservator.model.Model;
import com.futurice.android.reservator.model.Reservation;
import com.futurice.android.reservator.model.ReservatorException;
import com.futurice.android.reservator.model.Room;
import com.futurice.android.reservator.model.TimeSpan;

import java.util.Calendar;
import java.util.Locale;
import java.util.Vector;

public class TrafficLightsPresenter implements
        TrafficLightsPageFragment.TrafficLightsPagePresenter,
        RoomStatusFragment.RoomStatusPresenter,
        RoomReservationFragment.RoomReservationPresenter,
        DayCalendarFragment.DayCalendarPresenter,
        OngoingReservationFragment.OngoingReservationPresenter,
        com.futurice.android.reservator.common.Presenter,
        com.futurice.android.reservator.model.DataUpdatedListener,
        com.futurice.android.reservator.model.AddressBookUpdatedListener {

    final int QUICK_BOOK_THRESHOLD = 5; // minutes
    final int MAX_QUICK_BOOK_MINUTES = 120; //minutes
    final int DEFAULT_MINUTES = 45;

    private TrafficLightsPageFragment trafficLightsPageFragment;
    private RoomStatusFragment roomStatusFragment;
    private RoomReservationFragment roomReservationFragment;
    private OngoingReservationFragment ongoingReservationFragment;
    private DayCalendarFragment dayCalendarFragment;

    private Activity activity;
    private Model model;
    private Resources resources;

    private Room room;

    private Handler handler = new Handler();
    private Runnable minuteRunnable = new Runnable() {
        @Override
        public void run() {
            onMinuteElapsed();
            handler.postDelayed(this, 60000);
        }
    };

    private void refreshModel() {
        if (this.room != null)
            this.model.getDataProxy().refreshRoomReservations(this.room);
    }

    public void onMinuteElapsed() {
        this.refreshModel();
        this.updateOngoingReservationFragment();
    }

    public TrafficLightsPresenter(Activity activity, Model model) {
        this.activity = activity;
        this.resources = activity.getResources();

        this.model = model;
        this.model.getDataProxy().addDataUpdatedListener(this);
        this.model.getAddressBook().addDataUpdatedListener(this);

    }

    private void tryStarting() {
        if (trafficLightsPageFragment != null && roomStatusFragment != null &&
                ongoingReservationFragment != null && roomReservationFragment != null && dayCalendarFragment != null) {
            this.model.getDataProxy().refreshRoomReservations(this.model.getFavoriteRoom());
            handler.postDelayed(minuteRunnable, 60000);
        }
    }

    private void makeReservation(TimeSpan timespan, String description) {
        try {
            String accountEmail = PreferenceManager.getInstance(this.activity).getDefaultUserName();
            this.model.getDataProxy().reserve(room, timespan, description, accountEmail);
        } catch (ReservatorException e) {
            Log.d("Reservator", e.toString());
        }
    }

    private void cancelCurrentReservation() {
        try {
            this.model.getDataProxy().cancelReservation(this.room.getCurrentReservation());
        } catch (ReservatorException e) {
                Log.d("Reservator", e.toString());
            }
    }

    private void modifyCurrentReservationTimeSpan(TimeSpan timeSpan) {
        try {
            this.model.getDataProxy().modifyReservationTimeSpan(this.room.getCurrentReservation(), this.room, timeSpan);
        } catch (ReservatorException e) {
            Log.d("Reservator", e.toString());
        }
    }


    // ------ Implementation of RoomReservationFragment.RoomReservationPresenter

    @Override
    public void setRoomReservationFragment(RoomReservationFragment fragment) {
        this.roomReservationFragment = fragment;
        this.roomReservationFragment.setMaxMinutes(MAX_QUICK_BOOK_MINUTES);
        this.roomReservationFragment.setMinutes(DEFAULT_MINUTES);
        this.tryStarting();
    }

    @Override
    public void onReservationRequestMade(int minutes, String description) {
        Log.d("", "TrafficLightsPresenter::onReservationRequestMade() minutes: " + minutes + " description: " + description);

        TimeSpan timeSpan = new TimeSpan(new DateTime(), new DateTime(System.currentTimeMillis() + (minutes * 60 * 1000)));
        String tempDescription = resources.getString(R.string.default_reservation_description);

        if (description != null && !"".equals(description))
            tempDescription = description;

        this.makeReservation(timeSpan, tempDescription);
    }

    // ------ Implementation of OngoingReservationFragment.OngoingReservationPresenter

    @Override
    public void setOngoingReservationFragment(OngoingReservationFragment fragment) {
        this.ongoingReservationFragment = fragment;

        //this.roomReservationFragment.setTimeLimits(System.currentTimeMillis(), System.currentTimeMillis() + 1000*60*120);
        this.tryStarting();
    }

    @Override
    public void onReservationMinutesChanged(int newMinutes) {
        if (newMinutes <= 0) {
            this.cancelCurrentReservation();
        }
        else {
            Reservation reservation = this.room.getCurrentReservation();
            DateTime startTime = reservation.getStartTime();

            DateTime newEndTime = new DateTime(startTime.getTimeInMillis() + (newMinutes* 60 * 1000));
            this.modifyCurrentReservationTimeSpan(new TimeSpan(startTime,newEndTime));
        }
    }

    // ------ Implementation of TrafficLightsPageFragment.TrafficLightsPagePresenter

    @Override
    public void setTrafficLightsPageFragment(TrafficLightsPageFragment fragment) {
        this.trafficLightsPageFragment = fragment;
        this.tryStarting();
    }

    // ------- Implementation of RoomStatusFragment.RoomStatusPresenter

    @Override
    public void setRoomStatusFragment(RoomStatusFragment fragment) {
        this.roomStatusFragment = fragment;
        this.tryStarting();
    }

    // ------- Implementation of DayCalendarFragment.DayCalendarPresenter

    @Override
    public void setDayCalendarFragment(DayCalendarFragment fragment) {
        this.dayCalendarFragment = fragment;
        this.tryStarting();
    }

    // ------- Implementation of model.DataUpdatedListener

    @Override
    public void roomListUpdated(Vector<Room> rooms) {

    }

    @Override
    public void roomReservationsUpdated(Room room) {
        this.updateRoomData(room);
    }

    @Override
    public void refreshFailed(ReservatorException ex) {

    }

    // ------- Implementation of model.AddressBookUpdatedListener

    @Override
    public void addressBookUpdated() {

    }

    @Override
    public void addressBookUpdateFailed(ReservatorException e) {

    }


    //Methods for updating the view according to model's Room object

    /*
    private void updateConnected() {
        ConnectivityManager cm = null;
        try {
            cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        } catch (ClassCastException cce) {
            return;
        }
        if (cm == null) return;

        NetworkInfo ni = cm.getActiveNetworkInfo();

        if (ni != null && ni.isConnectedOrConnecting()) {
            // Connected
            lastTimeConnected = new Date();
            if (disconnected.getVisibility() != GONE) {
                disconnected.setVisibility(GONE);
            }
        } else {
            // Disconnected
            if (lastTimeConnected.before(new Date(new Date().getTime() - DISCONNECTED_WARNING_ICON_THRESHOLD))) {
                if (disconnected.getVisibility() != VISIBLE) {
                    disconnected.setVisibility(VISIBLE);
                }
            }
        }
    }
    */
    private void showReservationDetails(Reservation r, TimeSpan nextFreeSlot) {
        if (r == null) {
            this.roomStatusFragment.setMeetingNameText("");
        } else {
            this.roomStatusFragment.setMeetingNameText(r.getSubject());
        }

        if (nextFreeSlot == null) {
            // More than a day away
            this.roomStatusFragment.setStatusUntilText("");
        } else {
            String temp = resources.getString(R.string.free_at);
            this.roomStatusFragment.setStatusUntilText(Html.fromHtml(String.format(
                    Locale.getDefault(),
                    temp + " <b>%02d:%02d</b>",
                    nextFreeSlot.getStart().get(Calendar.HOUR_OF_DAY),
                    nextFreeSlot.getStart().get(Calendar.MINUTE))).toString());
        }
    }

    private void updateOngoingReservationFragment() {
        if (this.room == null)
            return;

        Reservation currentReservation = this.room.getCurrentReservation();
        if (currentReservation == null)
            return;


        int tempMax = MAX_QUICK_BOOK_MINUTES;

        if (room.isFreeAt(currentReservation.getEndTime())) {
            tempMax = room.minutesFreeFrom(currentReservation.getEndTime());
        }

        if (tempMax > MAX_QUICK_BOOK_MINUTES)
            tempMax = MAX_QUICK_BOOK_MINUTES;

        long endTime = currentReservation.getEndTime().getTimeInMillis();
        int remainingMinutes = (int) Math.round((endTime - System.currentTimeMillis()) / 60000f);

        if (this.ongoingReservationFragment != null) {
            this.ongoingReservationFragment.setMaxMinutes(tempMax);
            this.ongoingReservationFragment.setRemainingMinutes(remainingMinutes);
        }

        if (remainingMinutes <= 0) {
            this.refreshModel();
        }
    }

    private void showReserved() {
        this.trafficLightsPageFragment.getView().setBackgroundColor(resources.getColor(R.color.TrafficLightReserved));
        this.roomStatusFragment.setStatusText(resources.getString(R.string.status_reserved));
        this.updateOngoingReservationFragment();
        this.trafficLightsPageFragment.showOngoingReservationFragment();


        this.roomStatusFragment.hideBookNowText();
        this.showReservationDetails(room.getCurrentReservation(), room.getNextFreeSlot());
    }

    private void showReservationPending(int freeMinutes, DateTime freeAt) {

        this.roomStatusFragment.setStatusText(resources.getString(R.string.status_free));
        this.roomStatusFragment.setStatusUntilText(resources.getString(R.string.free_for_specific_amount)+" "+Helpers.dateTimeTo24h(freeAt));
        this.roomStatusFragment.setMeetingNameText("");

        this.trafficLightsPageFragment.getView().setBackgroundColor(resources.getColor(R.color.TrafficLightYellow));

        //this.trafficLightsPageFragment.showOngoingReservationFragment();
        //this.updateOngoingReservationFragment();
        this.roomStatusFragment.hideBookNowText();
        this.trafficLightsPageFragment.hideBothReservationFragments();
    }

    private void showFreeForRestOfTheDay() {
        this.roomStatusFragment.setStatusText(resources.getString(R.string.status_free));
        this.roomStatusFragment.setMeetingNameText("");
        this.roomStatusFragment.setStatusUntilText(resources.getString(R.string.free_for_the_day));
        this.roomStatusFragment.showBookNowText();

        this.roomReservationFragment.setMaxMinutes(MAX_QUICK_BOOK_MINUTES);
        this.trafficLightsPageFragment.getView().setBackgroundColor(resources.getColor(R.color.TrafficLightFree));
        this.trafficLightsPageFragment.showRoomReservationFragment();

    }

    private void showFreeForMinutes(int freeMinutes, DateTime freeAt) {
        this.roomStatusFragment.setStatusText(resources.getString(R.string.status_free));
        this.roomStatusFragment.setStatusUntilText(resources.getString(R.string.free_for_specific_amount)+" "+Helpers.dateTimeTo24h(freeAt));

        this.roomStatusFragment.setStatusText(resources.getString(R.string.status_free));
        this.roomStatusFragment.setMeetingNameText("");

        this.trafficLightsPageFragment.getView().setBackgroundColor(resources.getColor(R.color.TrafficLightFree));
        this.trafficLightsPageFragment.showRoomReservationFragment();
        this.roomStatusFragment.showBookNowText();

        int tempMinutes = MAX_QUICK_BOOK_MINUTES;

        if (freeMinutes < MAX_QUICK_BOOK_MINUTES)
            tempMinutes = freeMinutes;
        this.roomReservationFragment.setMaxMinutes(tempMinutes);

        this.trafficLightsPageFragment.showRoomReservationFragment();
    }


    public void updateRoomData(Room room) {
        //updateConnected();
        this.room = room;

        this.dayCalendarFragment.updateRoomData(room);
        this.roomStatusFragment.setRoomTitleText(room.getName());

        if (room.isBookable(QUICK_BOOK_THRESHOLD)) {
            if (room.isFreeRestOfDay()) {
                this.showFreeForRestOfTheDay();
            } else {
                int freeMinutes = room.minutesFreeFromNow();
                DateTime freeAt = room.getNextFreeSlot().getStart();
                if (freeMinutes >= Room.RESERVED_THRESHOLD_MINUTES) {
                    this.showFreeForMinutes(freeMinutes, freeAt);
                } else {
                    this.showReservationPending(freeMinutes, freeAt);
                }
            }
        } else {
            this.showReserved();
        }
    }
    /*
    public void updateRoomData(Room room) {
        //updateConnected();
        this.room = room;

        this.dayCalendarFragment.updateRoomData(room);
        this.roomStatusFragment.setRoomTitleText(room.getName());


        if (room.isBookable(QUICK_BOOK_THRESHOLD)) {
            this.roomStatusFragment.setStatusText(resources.getString(R.string.status_free));
            this.roomStatusFragment.setMeetingNameText("");

            if (room.isFreeRestOfDay()) {
                this.showFreeForRestOfTheDay();
                //this.roomStatusFragment.setStatusUntilText(resources.getString(R.string.free_for_the_day));
                //this.trafficLightsPageFragment.getView().setBackgroundColor(resources.getColor(R.color.TrafficLightFree));
                //this.trafficLightsPageFragment.showRoomReservationFragment();
                //this.roomStatusFragment.showBookNowText();
            } else {
                int freeMinutes = room.minutesFreeFromNow();
                //this.roomStatusFragment.setStatusText(resources.getString(R.string.status_free));
                //this.roomStatusFragment.setStatusUntilText(resources.getString(R.string.free_for_specific_amount, Helpers.humanizeTimeSpan2(freeMinutes)));

                if (freeMinutes >= Room.RESERVED_THRESHOLD_MINUTES) {
                    this.showFreeForMinutes(freeMinutes);
                    //this.trafficLightsPageFragment.getView().setBackgroundColor(resources.getColor(R.color.TrafficLightFree));
                    //this.trafficLightsPageFragment.showRoomReservationFragment();
                    //this.roomStatusFragment.showBookNowText();

                } else {
                    this.showReservationPending(freeMinutes);
                    //this.trafficLightsPageFragment.getView().setBackgroundColor(resources.getColor(R.color.TrafficLightYellow));
                    //this.trafficLightsPageFragment.showOngoingReservationFragment();
                    //this.updateOngoingReservationFragment();
                    //this.roomStatusFragment.hideBookNowText();
                }
            }

            //this.trafficLightsPageFragment.showRoomReservationFragment();

        } else {
            this.showReserved();

            //this.trafficLightsPageFragment.getView().setBackgroundColor(resources.getColor(R.color.TrafficLightReserved));
            //this.roomStatusFragment.setStatusText(resources.getString(R.string.status_reserved));
            //this.trafficLightsPageFragment.showOngoingReservationFragment();
            //this.updateOngoingReservationFragment();

            //this.roomStatusFragment.hideBookNowText();
            //this.showReservationDetails(room.getCurrentReservation(), room.getNextFreeSlot());
        }
    }*/
}
