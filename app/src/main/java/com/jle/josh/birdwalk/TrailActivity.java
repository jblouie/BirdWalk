package com.jle.josh.birdwalk;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

import static com.google.maps.android.SphericalUtil.computeArea;
import static com.google.maps.android.SphericalUtil.computeLength;

/**
 * Created by Josh
 * Trail activity
 * Displays map snippet, bullet points, user can read narratives, and launch directions
 */

public class TrailActivity extends AppCompatActivity {
    GoogleMap mMap;
    Trail trail;
    String trailName;
    Marker lastOpened = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trail);

        Intent intent = getIntent();
        trailName = intent.getExtras().getString("trailKey");
        trail = TrailData.getValue(trailName);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(trailName);
        setSupportActionBar(toolbar);

        //if a trail pic exists, set up correct button
        ImageButton ib;
        try {
            Class res = R.drawable.class;
            Field field = res.getField(trail.getBgName());
            int drawableId = field.getInt(null);
            ib = (ImageButton) findViewById(R.id.submitPic);
            ib.setVisibility(View.GONE);
        }
        catch (Exception e) {
            //Log.e("MyTag", "Failure to get drawable id.", e);
            ib = (ImageButton) findViewById(R.id.showPic);
            ib.setVisibility(View.GONE);
        }

        setUpMapIfNeeded();
        setUpInfo();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.normal:
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                return true;

            case R.id.hybrid:
                mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                return true;

            case R.id.satellite:
                mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                return true;

            case R.id.terrain:
                mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                return true;


            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    private void setUpMapIfNeeded() {
        if (mMap == null) {
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        //suppress all map gestures
        //turn off current location button
        mMap.getUiSettings().setAllGesturesEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
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
        //override click and longclick listeners, direct to full page map
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng position) {
                Intent intent = new Intent(TrailActivity.this, MapActivity.class);
                intent.putExtra("trailKey", trailName);
                intent.putExtra("fromActivity", "TrailActivity");
                startActivity(intent);
            }
        });
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng position) {
                Intent intent = new Intent(TrailActivity.this, MapActivity.class);
                intent.putExtra("trailKey", trailName);
                intent.putExtra("fromActivity", "TrailActivity");
                startActivity(intent);
            }
        });

        createTrailLine();
    }

    //draw trail line
    private void createTrailLine(){
        mMap.addMarker(new MarkerOptions().position(trail.getStart()).title("Start"));
        if (!trail.lotIsStart()) {
            mMap.addMarker(new MarkerOptions().position(trail.getLotPoint()).title("Parking"));
        }

        //trail only has 1 point
        if (trail.singlePoint()){
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(trail.getStart(), 15));
        }
        //special case, disjointed points
        else if (trail.getTypeCode() == 3){
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (int i = 0; i < trail.numPoints(); i++) {
                mMap.addMarker(new MarkerOptions().position(trail.getPoints()[i])
                        .title("View Point #" + (i+1)));
                builder.include(trail.getPoints()[i]);
            }
            LatLngBounds bounds = builder.build();
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.getCenter(), 14));
        }
        //trail has multiple points
        else{
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            //build polygon
            if (trail.getTypeCode() == 2){
                PolygonOptions trailPolygon = new PolygonOptions().fillColor(0x7F00FF00)
                        .strokeColor(Color.GREEN).strokeWidth(0);
                for (LatLng l : trail.getPoints()) {
                    trailPolygon.add(l);
                    builder.include(l);
                }
                mMap.addPolygon(trailPolygon);
            }
            //build polyline
            else {
                PolylineOptions trailLine = new PolylineOptions().color(Color.GREEN).width(5);
                for (LatLng l : trail.getPoints()) {
                    trailLine.add(l);
                    builder.include(l);
                }
                mMap.addPolyline(trailLine);
            }
            //center to bounds, zoom when map loaded
            LatLngBounds bounds = builder.build();
            int padding = 75; // offset from edges of the map in pixels
            final CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.getCenter(), 14));
            mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                @Override
                public void onMapLoaded() {
                    mMap.animateCamera(cu);
                }
            });
        }
    }

    //method for rounding double to specified places
    private static double round(double value, int places) {
        //http://stackoverflow.com/questions/2808535/round-a-double-to-2-decimal-places
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    //set up info: address, length, birds, habitats
    private void setUpInfo(){
        TextView tv1 = (TextView) findViewById(R.id.addrText);
        tv1.setText(trail.getAddress());
        //long click copies address to clipboard
        tv1.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Address", trail.getAddress());
                clipboard.setPrimaryClip(clip);
                Toast toast = Toast.makeText(getApplicationContext(), "Address copied to clipboard", Toast.LENGTH_SHORT);
                toast.show();
                return false;
            }
        });

        TextView tv3 = (TextView) findViewById(R.id.birdsText);
        tv3.setText(trail.getBirds());
        TextView tv4 = (TextView) findViewById(R.id.typeText);
        tv4.setText(trail.getHabitats());

        //set length icon and text for loop, area, site, one-way
        TextView tv2 = (TextView) findViewById(R.id.distText);
        ImageView len_icon = (ImageView) findViewById(R.id.len_icon);

        //set length icon to trail type
        int typeCode = trail.getTypeCode();
        switch (typeCode) {
            case 0: if (trail.singlePoint()){
                        len_icon.setImageResource(R.drawable.icon_pin);
                        tv2.setText("Birding Viewpoint");
                    }
                    else {
                        len_icon.setImageResource(R.drawable.icon_oneway_1);
                        double length = computeLength(Arrays.asList(trail.getPoints()));
                        double miles = round(length * .000621371,2);
                        String distString = Double.toString(miles)+" miles";
                        tv2.setText(distString);
                    }
                    break;
            case 1: len_icon.setImageResource(R.drawable.icon_loop_1);
                double length = computeLength(Arrays.asList(trail.getPoints()));
                double miles = round(length * .000621371,2);
                String distString = Double.toString(miles)+" miles";
                tv2.setText(distString);
                break;
            case 2: len_icon.setImageResource(R.drawable.icon_area3_1);
                double area = computeArea(Arrays.asList(trail.getPoints()));
                double sqmiles = round(area * .00000038610216,2);
                String areaString = Double.toString(sqmiles)+" miles\u00b2";
                tv2.setText(areaString);
                break;
            case 3: len_icon.setImageResource(R.drawable.icon_pin);
                tv2.setText("Birding Viewpoints");
                break;
            case 4: len_icon.setImageResource(R.drawable.icon_trailhead_1);
                tv2.setText("Trailhead");
                break;
            case 5: len_icon.setImageResource(R.drawable.icon_drive_1);
                double len = computeLength(Arrays.asList(trail.getPoints()));
                double mi = round(len * .000621371,2);
                String dist = Double.toString(mi)+" miles";
                tv2.setText(dist);
                break;
        }
    }

    //show icon legend dialog
    public void showLegend(View view){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater)
                this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.legend, null);
        builder.setView(v);
        AlertDialog ad = builder.create();
        ad.show();
    }

    //launch google maps app for directions to trail
    public void launchDirections(View view){
        //convert the starting latlng into a string
        LatLng lotPoint = trail.getLotPoint();
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

    //launch narrative w/ info activity
    public void launchExcerpt(View view){
        Intent intent = new Intent(TrailActivity.this, InfoActivity.class);
        intent.putExtra("fromActivity", "TrailActivity");
        intent.putExtra("resName", trail.getExcName());
        intent.putExtra("trailName", trailName);
        startActivity(intent);
    }

    //show submit pics dialog
    public void showSubmit(View view){
        LayoutInflater inflater = (LayoutInflater)
                this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.submit_pics, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(v);
        AlertDialog ad = builder.create();
        ad.show();
    }

    //launch upload activity
    public void launchSubmit(View view){
        Intent intent = new Intent(this, UploadActivity.class);
        intent.putExtra("fromActivity", "TrailActivity");
        intent.putExtra("trailName", trailName);
        startActivity(intent);
    }

    //show trail picture in dialog
    public void showPicture(View view){
        //http://stackoverflow.com/questions/7693633/android-image-dialog-popup
        int drawableId = 0;
        try {
            Class res = R.drawable.class;
            Field field = res.getField(trail.getBgName());
            drawableId = field.getInt(null);
        }
        catch (Exception e){
            Toast.makeText(getApplicationContext(),"No picture for "+ trailName,Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog builder = new Dialog(this);
        builder.requestWindowFeature(Window.FEATURE_NO_TITLE);
        builder.getWindow().setBackgroundDrawable(
                new ColorDrawable(android.graphics.Color.TRANSPARENT));
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                //nothing;
            }
        });

        ImageView imageView = new ImageView(this);
        imageView.setImageResource(drawableId);
        builder.addContentView(imageView, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        builder.show();
    }



}
