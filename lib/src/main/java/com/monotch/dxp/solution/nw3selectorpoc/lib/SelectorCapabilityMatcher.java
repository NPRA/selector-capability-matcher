package com.monotch.dxp.solution.nw3selectorpoc.lib;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monotch.dxp.solution.nw3selectorpoc.lib.filter.TrileanExpression;
import com.monotch.quadtree.QuadTreeHelper;

//import com.monotch.dxp.solution.nw3selectorpoc.lib.SelectorParser;
import org.apache.qpid.server.filter.PropertyExpression;
import org.apache.qpid.server.filter.PropertyExpressionFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SelectorCapabilityMatcher {

    private final ObjectMapper mapper = new ObjectMapper();

    boolean match(String selector, Map<String, Object> capabilityMap) {
        if (selector.isEmpty()) {
            return true;
        }
        try {

            SelectorParser<Map<String, Object>> selectorParser = new SelectorParser<>();
            selectorParser.setPropertyExpressionFactory(new PropertyExpressionFactory<Map<String, Object>>() {
                @Override
                public PropertyExpression<Map<String, Object>> createPropertyExpression(String value) {
                    return new PropertyExpression<Map<String, Object>>() {
                        @Override
                        public Object evaluate(Map<String, Object> object) {
                            return object.get(value);
                        }
                    };
                }
            });
            TrileanExpression<Map<String, Object>> matcher = selectorParser.parse(selector);
            return matcher.matches(capabilityMap) != Trilean.FALSE;

            
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    boolean match(String selector, String capabilityJson) {
        if (selector.isEmpty()) {
            return true;
        }
        try {
            Map<String, Object> map = mapper.readValue(capabilityJson, Map.class);

            return match(selector, map);
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    boolean qtmatch(String selector, String capabilityJson) {
        if (selector.isEmpty()) {
            return true;
        }
        try {
            Map<String, Object> map = mapper.readValue(capabilityJson, Map.class);

            ArrayList<String> capTree = (ArrayList<String>)map.get("quadTree");
            if(capTree != null && selector.contains("quadTree")) {
                // we have a quadtree, we need to handle it a bit differently
                System.out.println("we have quadTree: "+capTree);

                //get selector tiles:
                ArrayList<String> selTree = new ArrayList<>();
                Pattern startqt = Pattern.compile("quadTree\\s*LIKE\\s*\'%,|quadTree\\s*not\\s*LIKE\\s*\'%,",Pattern.CASE_INSENSITIVE);
                Pattern midqt = Pattern.compile("[0123]+",Pattern.CASE_INSENSITIVE);
                Pattern endqt = Pattern.compile("%'",Pattern.CASE_INSENSITIVE);
                Matcher matchstartqt = startqt.matcher(selector);
                Matcher matchmidqt = midqt.matcher(selector);
                Matcher matchendqt = endqt.matcher(selector);
                int index = 0;
                while(matchstartqt.find(index)) {
                    int startend = matchstartqt.end();
                    matchendqt.find();
                    index = matchendqt.end();
                    int endstart = matchendqt.start();
                    String qt = selector.substring(startend, endstart);
                    if(matchmidqt.region(startend, endstart).matches()) {
                        System.out.println("qt: "+qt);
                        selTree.add(qt);
                    }
                }
                if(selTree.size() < 1)
                    throw new ParseException("The selector contains quadtrees, but it does not match quadtree selector pattern. Is there a typo in the selector? Selector: "+selector);
                
                //find the smallest tile:
                int smallestSize = 0;
                for(String t : selTree) {
                    if(t.length() > smallestSize) {
                        smallestSize = t.length();
                    }
                }
                System.out.println("smallest tile is size: "+smallestSize);
                
                // create tiles at the same size as the smalles selector tile, and search for a match.
                // TODO: what if the cap tile is smaller than the smallest sel tile -> Seems to be handled for now, no broken tests.
                for(String capQt : capTree) {
                    if( ! overlaps(capQt, selTree)) { // if there is no overlap between the cap qt and the sel qt, we dont need to search.
                        if(testTile(selector, map, capQt)) return true; //test the cap tile directly (should take care of cases where there is only NOT LIKE, and nothing else in the selector)
                        continue;
                    }
                    
                    // create a smaler tile within capQt the same size as the smallest selector tile. 
                    // TODO: could probably be optimized by finding the smallest sel tile within each capQt. we want to keep the tiles as large as possible.
                    String searchTile = padTile(capQt, smallestSize); 
                    QuadTreeHelper.direction dir = QuadTreeHelper.direction.R; //step direction
                    String downFromStart = QuadTreeHelper.getNeighbour(QuadTreeHelper.direction.D, capQt);
                    String candidate = null;
                    while(searchTile != null) {

                       //check match;
                        if(testTile(selector, map, searchTile)) return true;

                        // find the next tile:
                        candidate = QuadTreeHelper.getNeighbour(dir, searchTile);
                        if( ! candidate.startsWith(capQt)) { // gone too far
                            
                            if(candidate.startsWith(downFromStart)) { // we are bellow the start tile. time to quit.
                                System.out.println("done");
                                searchTile = null;
                            }

                            // move down one tile
                            searchTile = QuadTreeHelper.getNeighbour(QuadTreeHelper.direction.D, searchTile);
                            if(searchTile.startsWith(downFromStart)) { // we are bellow the start tile. time to quit.
                                System.out.println("down");
                                searchTile = null;
                            }
                            if(dir == QuadTreeHelper.direction.R) { // start moving left
                               dir = QuadTreeHelper.direction.L;
                            }
                            else {// start moving right
                                dir = QuadTreeHelper.direction.R;
                            }
                        }
                        else
                            searchTile = candidate;
                    } 
                }
            }
            System.out.println("no qt found");
            return match(selector,map);
            
        } catch (IllegalStateException | JsonProcessingException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean testTile(String selector, Map<String,Object> map, String searchTile) {
        String newQt = ","+searchTile+",";
        map.put("quadTree",newQt);
        //System.out.println(map);
        if(match(selector, map)) {
            System.out.println("found match for: "+searchTile);
            return true;
        }
        System.out.println("no match for: "+searchTile);
        return false;
    }

    private String padTile(String startTile, int finalLength) {
        System.out.println("padding st:"+startTile);
        StringBuilder res = new StringBuilder(startTile);
        int padLength = finalLength-startTile.length();
        if(startTile.length() > finalLength) 
            padLength=1;
        for(int i=0;i<padLength;i++){
            res.append('0');
        }
        return res.toString();
    }

    private boolean overlaps(String tile1, ArrayList<String> tileset) {
        for(String tile2 : tileset) {
            if(overlaps(tile1, tile2)) return true;
        }
        return false;
    }

    private boolean overlaps(String tile1, String tile2) {
        return (tile1.startsWith(tile2) || tile2.startsWith(tile1));
    }

    public static void main(String[] args) {
        SelectorCapabilityMatcher matcher = new SelectorCapabilityMatcher();
        System.out.println(matcher.qtmatch("foo = 'bar' and quadTree LIKe '%,001%' and quadTree not LIKe '%,001%'", "{\"foo\":\"bar\",\"quadTree\":[\"00\"]}"));// 

    }

}

