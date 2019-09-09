package org.hwyl.sexytopo.model.survey;

import org.hwyl.sexytopo.control.util.StationNamer;
import org.hwyl.sexytopo.control.util.SurveyTools;
import org.hwyl.sexytopo.control.util.Wrapper;
import org.hwyl.sexytopo.model.sketch.Sketch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;


public class Survey {

    public static final Station NULL_STATION = new Station("-");

    public static final char[] FORBIDDEN_CHARS = new char[]{':', '.', '\n', '\r', '/', '\\'};

    private String name;

    private Station origin = new Station(StationNamer.generateOriginName());
    private Station activeStation = origin;

    private Sketch planSketch = new Sketch();
    private Sketch elevationSketch = new Sketch();

    private Trip trip = null;

    private Map<Station, Set<SurveyConnection>> stationsToSurveyConnections = new HashMap<>();

    private boolean isSaved = true;
    private boolean isAutosaved = true;

    private Stack<Leg> legsInChronoOrder = new Stack<>();
    //private Stack<UndoEntry> undoStack = new Stack<>();

    public Survey(String name) {
        setName(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        for (char c : FORBIDDEN_CHARS) {
            name = name.replace(Character.toString(c), "");
        }
        if (name.equals("")) {
            name = "blank";
        }
        this.name = name;
    }

    public void setActiveStation(Station activeStation) {
        this.activeStation = activeStation;
    }

    public Station getActiveStation() {

        if (activeStation == null) { // this shouldn't happen, but just in case...
            setActiveStation(origin);
        }

        return activeStation;
    }

    public void setSaved(boolean isSaved) {
        this.isSaved = isSaved;
        planSketch.setSaved(isSaved);
        elevationSketch.setSaved(isSaved);

        if (!isSaved) {
            // saving doesn't mean it's autosaved, but unsaved data means autosave required
            setAutosaved(false);
        }
    }

    public boolean isSaved() {
        return isSaved && planSketch.isSaved() && elevationSketch.isSaved();
    }

    public void setAutosaved(boolean isAutosaved) {
        this.isAutosaved = isAutosaved;
    }

    public boolean isAutosaved() {
        return isAutosaved;
    }

    public Leg getMostRecentLeg() {
        return legsInChronoOrder.empty()? null : legsInChronoOrder.peek();
    }

    public void setPlanSketch(Sketch planSketch) {
        this.planSketch = planSketch;
    }

    public Sketch getPlanSketch() {
        return planSketch;
    }

    public void setElevationSketch(Sketch elevationSketch) {
        this.elevationSketch = elevationSketch;
    }

    public Sketch getElevationSketch() {
        return elevationSketch;
    }

    public Station getOrigin() {
        return origin;
    }

    public void setOrigin(Station origin) {
        this.origin = origin;
    }

    public List<Station> getAllStations() {
        return getAllStations(origin);
    }

    public List<Leg> getAllLegs() {
        return getAllLegs(origin);
    }

    public static List<Leg> getAllLegs(Station root) {
        List<Leg> legs = new ArrayList<>(root.getOnwardLegs());
        for (Leg leg : root.getConnectedOnwardLegs()) {
            legs.addAll(getAllLegs(leg.getDestination()));
        }
        return legs;
    }

    public List<Leg> getLastNLegs(int n) {
        int numberOfLegs = legsInChronoOrder.size();
        int start = Math.max(0, numberOfLegs - n);
        return legsInChronoOrder.subList(start, numberOfLegs);

    }

    public List<Leg> getAllLegsInChronoOrder() {
        return legsInChronoOrder;
    }


    public static List<Station> getAllStations(Station root) {

        List<Station> stations = new ArrayList<>();
        stations.add(root);

        for (Leg leg : root.getConnectedOnwardLegs()) {
            Station destination = leg.getDestination();
            stations.addAll(getAllStations(destination));
        }

        return stations;
    }


    public void connect(Station joinInThisSurvey, Survey survey, Station joinInOtherSurvey) {

        if (survey.getName().equals(getName())) {
            throw new IllegalArgumentException("Can't join a survey onto itself");
        } else if (isConnectedTo(survey)) {
            throw new IllegalArgumentException("Already connected to that survey");
        }

        SurveyConnection connection = new SurveyConnection(joinInOtherSurvey, survey);

        Set<SurveyConnection> connections;
        if (stationsToSurveyConnections.containsKey(joinInThisSurvey)) {
            connections = stationsToSurveyConnections.get(joinInThisSurvey);
        } else {
            connections = new HashSet<>();
            stationsToSurveyConnections.put(joinInThisSurvey, connections);
        }

        connections.add(connection);
    }


    public void disconnect(Station joinInThisSurvey, Survey otherSurvey) throws Exception {
        Set<SurveyConnection> connections = stationsToSurveyConnections.get(joinInThisSurvey);

        for (SurveyConnection connection : connections) {
            if (connection.otherSurvey == otherSurvey) {
                connections.remove(connection);
                if (connections.size() == 0) {
                    stationsToSurveyConnections.remove(joinInThisSurvey);
                }
                return;
            }
        }
        throw new Exception("tried to disconnect unconnected survey");

    }


    public boolean isConnectedTo(Survey other) {
        for (Station station : getConnectedSurveys().keySet()) {
            if (isStationConnectedTo(station, other)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStationConnectedTo(Station station, Survey other) {
        String surveyName = other.getName();
        if (!stationsToSurveyConnections.containsKey(station)) {
            return false;
        } else {
            Set<SurveyConnection> connections = stationsToSurveyConnections.get(station);
            for (SurveyConnection connection : connections) {
                if (connection.otherSurvey.getName().equals(surveyName)) {
                    return true;
                }
            }
            return false;
        }
    }

    public Map<Station, Set<SurveyConnection>> getConnectedSurveys() {
        return stationsToSurveyConnections;
    }

    public boolean hasLinkedSurveys(Station station) {
        return stationsToSurveyConnections.containsKey(station);
    }

    public void checkActiveStation() {
        List<Station> stations = getAllStations();
        if (!stations.contains(activeStation)) {
            activeStation = findNewActiveStation();
        }
    }

    private Station findNewActiveStation() {
        for (int i = legsInChronoOrder.size() - 1; i >= 0; i--) {
            Leg leg = legsInChronoOrder.elementAt(i);
            if (leg.hasDestination()) {
                return leg.getDestination();
            }
        }
        return origin;
    }


    public void addLegRecord(Leg leg) {
        legsInChronoOrder.push(leg);
    }


    public void deleteStation(final Station toDelete) {
        if (toDelete == getOrigin()) {
            return;
        }

        SurveyTools.traverseLegs(this, new SurveyTools.SurveyLegTraversalCallback() {
            @Override
            public boolean call(Station origin, Leg leg) {
                if (leg.hasDestination() && leg.getDestination() == toDelete) {
                    origin.getOnwardLegs().remove(leg);
                    checkActiveStation();
                    return true;
                } else {
                    return false;
                }
            }
        });

        setSaved(false);
    }


    public void deleteSplay(Station station, Leg splay) {
        station.getOnwardLegs().remove(splay);
        setSaved(false);
    }

    public Leg getReferringLeg(final Station station) {

        if (station == getOrigin()) {
            return null;
        }

        final Wrapper wrapper = new Wrapper();
        SurveyTools.traverseLegs(
                this,
                new SurveyTools.SurveyLegTraversalCallback() {
                    @Override
                    public boolean call(Station origin, Leg leg) {
                        if (leg.getDestination() == station) {
                            wrapper.value = leg;
                            return true;
                        } else {
                            return false;
                        }
                    }
                });
        return (Leg)(wrapper.value);
    }


    public Station getOriginatingStation(final Leg leg) {
        final Wrapper wrapper = new Wrapper();
        SurveyTools.traverseStations(
                this,
                new SurveyTools.SurveyStationTraversalCallback() {
                    @Override
                    public boolean call(Station station) {
                        if (station.getOnwardLegs().contains(leg)) {
                            wrapper.value = station;
                            return true;
                        } else {
                            return false;
                        }
                    }
                });

        return (Station)(wrapper.value);
    }


    public void undoAddLeg() {

        if (legsInChronoOrder.size() < 1) {
            return;
        }

        final Leg toDelete = legsInChronoOrder.pop();
        SurveyTools.traverseLegs(
            this,
            new SurveyTools.SurveyLegTraversalCallback() {
                @Override
                public boolean call(Station origin, Leg leg) {
                    if (leg == toDelete) {
                        origin.getOnwardLegs().remove(toDelete);
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        );

        setSaved(false);
        checkActiveStation();
    }

    public Station getStationByName(final String name) {
        final Wrapper wrapper = new Wrapper();
        SurveyTools.traverseStations(
                this,
                new SurveyTools.SurveyStationTraversalCallback() {
                    @Override
                    public boolean call(Station station) {
                        if (station.getName().equals(name)) {
                            wrapper.value = station;
                            return true;
                        } else {
                            return false;
                        }
                    }
                });

        return (Station)(wrapper.value);
    }


    public Trip getTrip() {
        return trip;
    }


    public void setTrip(Trip trip) {
        this.trip = trip;
    }


    public String toString() {
        return "[Survey " + getName() + "]";
    }


    private class UndoEntry {
        private Station station;
        private Leg leg;
        private UndoEntry(Station station, Leg leg) {
            this.station = station;
            this.leg = leg;
        }
    }


}
