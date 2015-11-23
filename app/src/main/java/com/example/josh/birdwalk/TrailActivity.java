package com.example.josh.birdwalk;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;

public class TrailActivity extends AppCompatActivity {

    GoogleMap mMap;
    TrailData trailData;
    Trail currTrail;
    String trailName;
    Marker lastOpened = null;
    float distance;

    public final static String EXTRA_EXCNAME = "com.example.josh.birdwalk.EXCNAME";
    public final static String EXTRA_TRAILNAME = "com.example.josh.birdwalk.TRAILNAME";
    public final static String EXTRA_TRAILKEY = "com.example.josh.birdwalk.TRAILKEY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trail);

        Intent intent = getIntent();
        trailName = intent.getStringExtra(MapActivity.EXTRA_TRAILKEY);
        trailData = TrailData.getInstance();
        currTrail = trailData.getValue(trailName);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(trailName);
        setSupportActionBar(toolbar);

        Button button2 = (Button) this.findViewById(R.id.excerpt);
        button2.setText("Read more about " + trailName);

        setUpMapIfNeeded();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    public void makeToast(){
        Toast.makeText(this, "no permission", Toast.LENGTH_SHORT).show();
    }

    private void setUpMap() {
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        //suppress all map gestures
        //turn off current location button
        mMap.getUiSettings().setAllGesturesEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            // Show rationale and request permission.
//            makeToast();
        }

        //override the markerclicklistener
        //suppress automatic pan onclick
        //http://stackoverflow.com/questions/14497734/dont-snap-to-marker-after-click-in-android-map-v2
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            public boolean onMarkerClick(Marker marker) {
                // Check if there is an open info window
                if (lastOpened != null) {
                    // Close the info window
                    lastOpened.hideInfoWindow();
                    // Is the marker the same marker that was already open
                    if (lastOpened.equals(marker)) {
                        // Nullify the lastOpened object
                        lastOpened = null;
                        // Return so that the info window isn't opened again
                        return true;
                    }
                }
                // Open the info window for the marker
                marker.showInfoWindow();
                // Re-assign the last opened such that we can close it later
                lastOpened = marker;
                // Event was handled by our code do not launch default behaviour.
                return true;
            }
        });
        //override onmapclicklistener, direct to full page map
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener(){
            @Override
            public void onMapClick(LatLng position){
                Intent intent = new Intent(TrailActivity.this, MapActivity.class);
                intent.putExtra(EXTRA_TRAILKEY, trailName);
                intent.putExtra("fromActivity", "TrailActivity");
                startActivity(intent);
            }
        });

        addTrailLine();
    }

    public void addTrailLine(){
        //calculate the avg lat/lng of the trail markers
        Double avgLat = 0.0;
        Double avgLng = 0.0;
        //calculate total trail distance
        distance = 0;

        //add points to the polyline
        int numPoints = currTrail.points.length;
        mMap.addMarker(new MarkerOptions().position(currTrail.lotPoint).title("Parking Lot"));
        mMap.addMarker(new MarkerOptions().position(currTrail.points[0]).title("Trail Start"));
        PolylineOptions TrailPoints = new PolylineOptions().color(Color.GREEN).width(5);
        for (int i = 0; i < numPoints; i++){
            TrailPoints.add(currTrail.points[i]);
            avgLat += currTrail.points[i].latitude;
            avgLng += currTrail.points[i].longitude;

            if (i < numPoints-1)
                distance += distanceBetween(currTrail.points[i], currTrail.points[i+1]);

        }
        mMap.addPolyline(TrailPoints);
        TextView tv = (TextView) findViewById(R.id.distText);
        double miles = round(distance * .000621371,2);
        double klicks = round(distance/1000,2);
        tv.setText("Trail Distance: "+ Double.toString(miles) + "miles or " + Double.toString(klicks) + "km");

        //center the camera to the avg position
        avgLat += currTrail.lotPoint.latitude;
        avgLng += currTrail.lotPoint.longitude;
        avgLat = avgLat/(numPoints+1);
        avgLng = avgLng/(numPoints+1);
        LatLng CENTER = new LatLng(avgLat, avgLng);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(CENTER, 15));
    }

    private float distanceBetween(LatLng latLng1, LatLng latLng2) {
        Location loc1 = new Location(LocationManager.GPS_PROVIDER);
        Location loc2 = new Location(LocationManager.GPS_PROVIDER);
        loc1.setLatitude(latLng1.latitude);
        loc1.setLongitude(latLng1.longitude);
        loc2.setLatitude(latLng2.latitude);
        loc2.setLongitude(latLng2.longitude);
        return loc1.distanceTo(loc2);
    }

    public static double round(double value, int places) {
        http://stackoverflow.com/questions/2808535/round-a-double-to-2-decimal-places
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public void launchDirections(View view){
        //convert the starting latlng into a string
        LatLng lotPoint = currTrail.lotPoint;
        Double lat = lotPoint.latitude;
        Double lng = lotPoint.longitude;
        String latString = lat.toString();
        String lngString = lng.toString();
        String uriString = "google.navigation:q=".concat(latString).concat(",").concat(lngString);

        //launch navigation to trail in Google Maps Apps
        Uri gmmIntentUri = Uri.parse(uriString);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        startActivity(mapIntent);
    }

    public void launchExcerpt(View view){
        Intent intent = new Intent(TrailActivity.this, InfoActivity.class);
        intent.putExtra("fromActivity", "TrailActivity");
        intent.putExtra(EXTRA_EXCNAME, currTrail.excName);
        intent.putExtra(EXTRA_TRAILNAME, trailName);
        startActivity(intent);
    }



}
