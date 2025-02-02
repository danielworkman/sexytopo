package org.hwyl.sexytopo.control.util;

import org.hwyl.sexytopo.model.graph.Coord2D;
import org.hwyl.sexytopo.model.graph.Line;
import org.hwyl.sexytopo.model.graph.Space;
import org.hwyl.sexytopo.model.sketch.PathDetail;
import org.hwyl.sexytopo.model.survey.Leg;
import org.hwyl.sexytopo.model.survey.Station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class Space2DUtils {

    public static double getDistanceFromLine(Coord2D point, Coord2D lineStart, Coord2D lineEnd) {

        // Adapted from a post on StackExchange by Joshua
        // http://stackoverflow.com/questions/849211/shortest-distance-between-a-point-and-a-line-segment

        double x = point.x;
        double y = point.y;
        double x1 = lineStart.x;
        double y1 = lineStart.y;
        double x2 = lineEnd.x;
        double y2 = lineEnd.y;

        double a = x - x1;
        double b = y - y1;
        double c = x2 - x1;
        double d = y2 - y1;

        double dot = a * c + b * d;
        double lenSq = c * c + d * d;
        double param = -1;

        if (lenSq != 0) {
            param = dot / lenSq;
        }

        double xx, yy;

        if (param < 0) {
            xx = x1;
            yy = y1;
        } else if (param > 1) {
            xx = x2;
            yy = y2;
        } else {
            xx = x1 + param * c;
            yy = y1 + param * d;
        }

        double dx = x - xx;
        double dy = y - yy;
        return Math.sqrt(dx * dx + dy * dy);
    }


    public static double getDistance(Coord2D a, Coord2D b) {
        return Math.sqrt(Math.pow((a.x - b.x), 2) + Math.pow((a.y - b.y), 2));
    }

    public static double adjustAngle(double angle, double delta) {
        double newAngle = angle + delta;
        while (newAngle < 0) {
            newAngle += 360;
        }
         return newAngle % 360;
    }

    @SuppressWarnings("ConstantConditions")
    public static Space<Coord2D> transform(Space<Coord2D> space, Coord2D point) {

        Space<Coord2D> newSpace = new Space<>();

        Map<Station, Coord2D> stations = space.getStationMap();
        for (Station station : stations.keySet()) {
            Coord2D coord = stations.get(station);
            Coord2D newCoord = coord.plus(point);
            newSpace.addStation(station, newCoord);
        }

        Map<Leg, Line<Coord2D>> legs = space.getLegMap();
        for (Leg leg : legs.keySet()) {
            Line<Coord2D> line = legs.get(leg);
            Line<Coord2D> newLine = transformLine(line, point);
            newSpace.addLeg(leg, newLine);
        }

        return newSpace;
    }

    public static Line<Coord2D> transformLine(Line<Coord2D> line, Coord2D point) {
        Coord2D start = line.getStart();
        Coord2D end = line.getEnd();
        return new Line<>(start.plus(point), end.plus(point));
    }

    private static List<Coord2D> douglasPeukerIteration(List<Coord2D> path, double epsilon) {

        // Find the point with the maximum distance
        int pathSize = path.size();
        int indexMax = 0;
        double distMax = 0;

        for (int i = 1; i != pathSize; ++i) {
            double dist = getDistanceFromLine(path.get(i), path.get(0), path.get(pathSize - 1));
            if (dist > distMax) {
                distMax = dist;
                indexMax = i;
            }
        }

        List<Coord2D> simplifiedPath;

        // If max distance is greater than epsilon, recursively simplify
        if (distMax > epsilon) {
            List<Coord2D> results1 = douglasPeukerIteration(path.subList(0, indexMax + 1), epsilon);
            List<Coord2D> results2 = douglasPeukerIteration(path.subList(indexMax, pathSize), epsilon);

            simplifiedPath = new ArrayList<>(results1);
            simplifiedPath.addAll(results2.subList(1, results2.size()));
        } else {
            simplifiedPath = new ArrayList<>();
            simplifiedPath.add(path.get(0));
            simplifiedPath.add(path.get(pathSize - 1));
        }

        return simplifiedPath;
    }

    public static double simplificationEpsilon(double width, double height) {

        // TODO: this is a pretty crude simplification factor but we can revisit after more testing
        double maxDim = Math.max(width, height);
        return Math.max(maxDim / 500, 0.001);
    }

    public static double simplificationEpsilon(PathDetail pathDetail) {
        return simplificationEpsilon(pathDetail.getWidth(), pathDetail.getHeight());
    }

    public static List<Coord2D> simplify(List<Coord2D> path, double epsilon) {

        if (path.isEmpty() || epsilon <= 0)
            return path;

        return douglasPeukerIteration(path, epsilon);
    }

}
