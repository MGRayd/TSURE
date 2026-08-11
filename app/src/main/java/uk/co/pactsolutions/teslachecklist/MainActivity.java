package uk.co.pactsolutions.teslachecklist;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 12, 18);
    private static final int SURFACE = Color.rgb(24, 28, 38);
    private static final int SURFACE_2 = Color.rgb(35, 40, 54);
    private static final int TESLA_RED = Color.rgb(220, 28, 46);
    private static final int TEXT = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(174, 181, 194);
    private static final int BORDER = Color.rgb(64, 72, 90);
    private static final int REQUEST_PICK_ISSUE_PHOTO = 2001;
    private static final int REQUEST_TAKE_ISSUE_PHOTO = 2002;

    private LinearLayout list;
    private TextView progress;
    private ProgressBar progressBar;
    private Spinner sectionSpinner;
    private ArrayAdapter<String> sectionAdapter;
    private boolean updatingSectionSpinner = false;
    private final ArrayList<ItemRow> rows = new ArrayList<>();
    private final ArrayList<CheckItem> activeChecks = new ArrayList<>();
    private final ArrayList<String> sectionNames = new ArrayList<>();
    private final LinkedHashMap<String, LinearLayout> sectionContentViews = new LinkedHashMap<>();
    private String openSection;
    private int pendingIssuePhotoIndex = -1;
    private Uri pendingCameraUri;
    private ImageView pendingIssuePhotoPreview;
    private android.content.SharedPreferences prefs;
    private android.content.SharedPreferences selectionPrefs;
    private AssetTeslaLocationRepository locationRepository;
    private boolean showingOrderDetails = false;
    private boolean orderDetailsOpenedFromChecklist = false;
    private boolean showingArchives = false;
    private boolean archivesOpenedFromChecklist = false;

    private static class CheckItem {
        String section;
        String text;
        CheckItem(String section, String text) { this.section = section; this.text = text; }
    }

    private class SwipeViewFlipper extends ViewFlipper {
        private float downX;
        private float downY;
        private boolean swiped;
        private final int touchSlop;
        private SwipeListener swipeListener;

        SwipeViewFlipper(Context context) {
            super(context);
            touchSlop = Math.max(dp(8), ViewConfiguration.get(context).getScaledTouchSlop());
        }

        void setSwipeListener(SwipeListener listener) {
            swipeListener = listener;
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                swiped = false;
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
            }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float dx = event.getX() - downX;
            if (!swiped && Math.abs(dx) >= dp(18)) {
                swiped = true;
                if (swipeListener != null) swipeListener.onSwipe(dx < 0 ? 1 : -1);
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
    }

    private interface SwipeListener {
        void onSwipe(int direction);
    }

    private static class CollectionLocationSelection {
        String selectedId;
        Button selector;
    }

    private class ItemRow {
        int index;
        CheckItem item;
        RadioGroup statusGroup;
        EditText notes;
        ItemRow(int index, CheckItem item, RadioGroup statusGroup, EditText notes) {
            this.index = index; this.item = item; this.statusGroup = statusGroup; this.notes = notes;
        }
    }

    private final CheckItem[] CHECKS = new CheckItem[] {
        new CheckItem("Before You Arrive", "Valid drivers licence"),
        new CheckItem("Before You Arrive", "Tesla app downloaded and logged in"),
        new CheckItem("Before You Arrive", "VIN and delivery appointment confirmed"),
        new CheckItem("Before You Arrive", "Paperwork or trade-in documents if required"),
        new CheckItem("Before You Arrive", "Phone fully charged"),
        new CheckItem("Before You Arrive", "Ensure insurance is active for collection day"),

        new CheckItem("Exterior Checks Stage 1", "Match VIN on windscreen against mobile app and purchase/lease agreement"),
        new CheckItem("Exterior Checks Stage 1", "No scratches, dents, or paint chips on body panels"),
        new CheckItem("Exterior Checks Stage 1", "Consistent panel gaps and alignment: doors, boot, frunk, charge port"),
        new CheckItem("Exterior Checks Stage 1", "No smudges or uneven paint blending"),
        new CheckItem("Exterior Checks Stage 1", "Door handles flush and functional"),
        new CheckItem("Exterior Checks Stage 1", "No loose trim or rubber seals"),

        new CheckItem("Exterior Checks Stage 1", "Windscreen and windows free from cracks, chips, or scratches"),
        new CheckItem("Exterior Checks Stage 1", "Roof glass free from distortion, cracks, chips, or scratches"),
        new CheckItem("Exterior Checks Stage 1", "Mirrors properly attached and functional"),

        new CheckItem("Exterior Checks Stage 1", "All rims scratch-free and undamaged"),
        new CheckItem("Exterior Checks Stage 1", "Tyres match the expected spec and correct size"),
        new CheckItem("Exterior Checks Stage 1", "Adequate tyre tread and proper inflation"),
        new CheckItem("Exterior Checks Stage 2", "Headlights, taillights, and indicators aligned and working"),
        new CheckItem("Exterior Checks Stage 2", "Cameras clean and lens covers intact"),
        new CheckItem("Exterior Checks Stage 2", "Frunk opens smoothly and seals properly"),
        new CheckItem("Exterior Checks Stage 2", "Boot opens/closes without resistance or misalignment"),
        new CheckItem("Exterior Checks Stage 2", "Frunk and boot carpeting clean and attached"),
        new CheckItem("Exterior Checks Stage 2", "Emergency triangle / first aid kit if applicable"),

        new CheckItem("Interior & Cabin Tech", "No marks, stains, or creases on seats"),
        new CheckItem("Interior & Cabin Tech", "All seat controls functional"),
        new CheckItem("Interior & Cabin Tech", "Seatbelts retract smoothly and latch securely"),
        new CheckItem("Interior & Cabin Tech", "Dashboard, centre console, and door trims scratch-free"),
        new CheckItem("Interior & Cabin Tech", "No rattling sounds when doors close"),

        new CheckItem("Interior & Cabin Tech", "Screen bright, responsive, and scratch-free"),
        new CheckItem("Interior & Cabin Tech", "Buttons, scroll wheels, and steering controls work"),
        new CheckItem("Interior & Cabin Tech", "Volume and climate controls respond properly"),
        new CheckItem("Interior & Cabin Tech", "Check software version in Settings > Software"),

        new CheckItem("Interior & Cabin Tech", "Test A/C and heater on all vents"),
        new CheckItem("Interior & Cabin Tech", "Test heated seats / cooling seats where applicable"),
        new CheckItem("Interior & Cabin Tech", "Bluetooth pairs with your phone"),
        new CheckItem("Interior & Cabin Tech", "Audio system works properly"),
        new CheckItem("Interior & Cabin Tech", "Check cameras are working on all sides of car"),

        new CheckItem("Interior & Cabin Tech", "All doors open, close, and lock smoothly"),
        new CheckItem("Interior & Cabin Tech", "Windows roll up/down without noise"),
        new CheckItem("Interior & Cabin Tech", "Child locks functional if applicable"),

        new CheckItem("Interior & Cabin Tech", "Mirrors auto-dim and adjust via controls"),
        new CheckItem("Interior & Cabin Tech", "Rear-view mirror properly aligned"),
        new CheckItem("Exterior Checks Stage 2", "Operate the wipers and confirm they move smoothly without smearing, juddering, or squeaking (Activate via steering wheel)"),
        new CheckItem("Exterior Checks Stage 2", "Washer fluid sprays correctly"),

        new CheckItem("Access, Charging & Documents", "Tesla app connects and unlocks car"),
        new CheckItem("Access, Charging & Documents", "Test mobile key and/or key card"),
        new CheckItem("Access, Charging & Documents", "Try remote climate control"),

        new CheckItem("Access, Charging & Documents", "Charge port door opens from app, screen, and touch, then closes properly"),
        new CheckItem("Access, Charging & Documents", "Mobile charger present if included"),
        new CheckItem("Access, Charging & Documents", "Test charger unlock button"),
        new CheckItem("Access, Charging & Documents", "Vehicle logbook / V5C submitted if UK"),
        new CheckItem("Access, Charging & Documents", "Confirm correct vehicle spec and VIN"),
        new CheckItem("Access, Charging & Documents", "Warranty, manual, and service info provided digitally"),
        new CheckItem("Final Checks", "Number plates correct and securely fitted"),
        new CheckItem("Final Checks", "Test horn"),
        new CheckItem("Final Checks", "Included accessories present: floor mats, sunshade, tow hook, etc."),
        new CheckItem("Final Checks", "Take a test drive around the lot/block if possible"),

        new CheckItem("Final Checks", "Set up home/work charging in Navigation"),
        new CheckItem("Final Checks", "Set Sentry Mode preferences"),
        new CheckItem("Final Checks", "Take a nice exterior photo of the car"),
        new CheckItem("Final Checks", "Take a nice interior photo of the cabin")
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        selectionPrefs = getSharedPreferences("tesla_checklist", MODE_PRIVATE);
        locationRepository = new AssetTeslaLocationRepository(this);
        migrateLegacyModel3Checklist();
        migrateRemovedDoorHandleCheck();
        migrateDuplicateChecks();
        migrateReviewedChecklist();
        migrateNewCollectionChecks();
        migrateGlobalOrderDetails();
        setChecklistPreferences(selectedModel());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                this::handleBackNavigation
            );
        }
        showLandingPage();
    }

    private String selectedModel() {
        return selectionPrefs.getString("selected_model", "Model 3");
    }

    private void setChecklistPreferences(String model) {
        String suffix = model.toLowerCase(Locale.UK).replace(" ", "_");
        prefs = getSharedPreferences("tesla_checklist_" + suffix, MODE_PRIVATE);
    }

    private android.content.SharedPreferences modelPreferences(String model) {
        String suffix = model.toLowerCase(Locale.UK).replace(" ", "_");
        return getSharedPreferences("tesla_checklist_" + suffix, MODE_PRIVATE);
    }

    private void migrateGlobalOrderDetails() {
        if (selectionPrefs.getBoolean("model_order_details_migrated", false)) return;

        android.content.SharedPreferences destination = modelPreferences(selectedModel());
        android.content.SharedPreferences.Editor editor = destination.edit();
        String[] keys = {
            "order_number", "order_vin", "order_collection_date",
            "order_collection_time", "order_collection_location",
            "order_edd_start", "order_edd_end"
        };
        for (String key : keys) {
            if (!destination.contains(key) && selectionPrefs.contains(key)) {
                editor.putString(key, selectionPrefs.getString(key, ""));
            }
        }
        editor.apply();
        selectionPrefs.edit().putBoolean("model_order_details_migrated", true).apply();
    }

    private void selectModel(String model) {
        selectionPrefs.edit().putString("selected_model", model).apply();
        setChecklistPreferences(model);
        showChecklistPage();
    }

    private void migrateLegacyModel3Checklist() {
        if (selectionPrefs.getBoolean("model_storage_migrated", false)) return;

        android.content.SharedPreferences model3Prefs =
            getSharedPreferences("tesla_checklist_model_3", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = model3Prefs.edit();
        for (Map.Entry<String, ?> entry : selectionPrefs.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if ("selected_model".equals(key) || "model_storage_migrated".equals(key)) continue;
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
        }
        editor.apply();
        selectionPrefs.edit().putBoolean("model_storage_migrated", true).apply();
    }

    private void migrateRemovedDoorHandleCheck() {
        if (selectionPrefs.getBoolean("door_handle_check_removed_migrated", false)) return;

        migrateChecklistAfterRemovedItem(getSharedPreferences("tesla_checklist_model_3", MODE_PRIVATE), 19);
        migrateChecklistAfterRemovedItem(getSharedPreferences("tesla_checklist_model_y", MODE_PRIVATE), 19);
        selectionPrefs.edit().putBoolean("door_handle_check_removed_migrated", true).apply();
    }

    private void migrateChecklistAfterRemovedItem(
        android.content.SharedPreferences checklistPrefs,
        int removedIndex
    ) {
        android.content.SharedPreferences.Editor editor = checklistPrefs.edit();
        String[] prefixes = {"status_", "notes_", "photo_"};
        final int checklistCountAfterRemoval = 70;
        for (int index = removedIndex; index < checklistCountAfterRemoval; index++) {
            for (String prefix : prefixes) {
                String destination = prefix + index;
                String source = prefix + (index + 1);
                if (!checklistPrefs.contains(source)) {
                    editor.remove(destination);
                } else if ("status_".equals(prefix)) {
                    editor.putInt(destination, checklistPrefs.getInt(source, -1));
                } else {
                    editor.putString(destination, checklistPrefs.getString(source, ""));
                }
            }
        }
        for (String prefix : prefixes) editor.remove(prefix + checklistCountAfterRemoval);
        editor.apply();
    }

    private void migrateDuplicateChecks() {
        if (selectionPrefs.getBoolean("duplicate_checks_removed_migrated", false)) return;

        migrateChecklistAfterDuplicateRemoval(getSharedPreferences("tesla_checklist_model_3", MODE_PRIVATE));
        migrateChecklistAfterDuplicateRemoval(getSharedPreferences("tesla_checklist_model_y", MODE_PRIVATE));
        selectionPrefs.edit().putBoolean("duplicate_checks_removed_migrated", true).apply();
    }

    private void migrateChecklistAfterDuplicateRemoval(android.content.SharedPreferences checklistPrefs) {
        android.content.SharedPreferences.Editor editor = checklistPrefs.edit();
        String[] prefixes = {"status_", "notes_", "photo_"};
        final int checklistCountAfterDuplicateRemoval = 68;
        for (int destination = 0; destination < checklistCountAfterDuplicateRemoval; destination++) {
            int source = destination < 18 ? destination : destination < 22 ? destination + 1 : destination + 2;
            if (source == destination) continue;
            for (String prefix : prefixes) {
                String destinationKey = prefix + destination;
                String sourceKey = prefix + source;
                if (!checklistPrefs.contains(sourceKey)) {
                    editor.remove(destinationKey);
                } else if ("status_".equals(prefix)) {
                    editor.putInt(destinationKey, checklistPrefs.getInt(sourceKey, -1));
                } else {
                    editor.putString(destinationKey, checklistPrefs.getString(sourceKey, ""));
                }
            }
        }
        for (int oldIndex = checklistCountAfterDuplicateRemoval; oldIndex < 70; oldIndex++) {
            for (String prefix : prefixes) editor.remove(prefix + oldIndex);
        }
        editor.apply();
    }

    private void migrateReviewedChecklist() {
        if (selectionPrefs.getBoolean("reviewed_checklist_2026_migrated", false)) return;

        migrateReviewedChecklistForModel(getSharedPreferences("tesla_checklist_model_3", MODE_PRIVATE));
        migrateReviewedChecklistForModel(getSharedPreferences("tesla_checklist_model_y", MODE_PRIVATE));
        selectionPrefs.edit().putBoolean("reviewed_checklist_2026_migrated", true).apply();
    }

    private void migrateReviewedChecklistForModel(android.content.SharedPreferences checklistPrefs) {
        final int oldBuiltInCount = 68;
        final int reviewedBuiltInCount = 60;
        final int[] oldIndexForNewIndex = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31,
            32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 47,
            48, 49, 50, 52, 53, 54, 57, 58, 59, 60, 63, 64, 65, 66
        };
        final int customCount = checklistPrefs.getInt("custom_count", 0);
        final Map<String, ?> snapshot = new HashMap<>(checklistPrefs.getAll());
        final android.content.SharedPreferences.Editor editor = checklistPrefs.edit();
        final String[] prefixes = {"status_", "notes_", "photo_"};

        for (int destination = 0; destination < oldIndexForNewIndex.length; destination++) {
            int source = oldIndexForNewIndex[destination];
            for (String prefix : prefixes) {
                copyStoredChecklistValue(editor, snapshot, prefix + source, prefix + destination);
            }
        }

        for (int customIndex = 0; customIndex < customCount; customIndex++) {
            int source = oldBuiltInCount + customIndex;
            int destination = reviewedBuiltInCount + customIndex;
            for (String prefix : prefixes) {
                copyStoredChecklistValue(editor, snapshot, prefix + source, prefix + destination);
            }
        }

        int newTotalCount = reviewedBuiltInCount + customCount;
        int oldTotalCount = oldBuiltInCount + customCount;
        for (int index = newTotalCount; index < oldTotalCount; index++) {
            for (String prefix : prefixes) editor.remove(prefix + index);
        }
        editor.apply();
    }

    private void migrateNewCollectionChecks() {
        if (selectionPrefs.getBoolean("new_collection_checks_2026_migrated", false)) return;

        migrateNewCollectionChecksForModel(getSharedPreferences("tesla_checklist_model_3", MODE_PRIVATE));
        migrateNewCollectionChecksForModel(getSharedPreferences("tesla_checklist_model_y", MODE_PRIVATE));
        selectionPrefs.edit().putBoolean("new_collection_checks_2026_migrated", true).apply();
    }

    private void migrateNewCollectionChecksForModel(
        android.content.SharedPreferences checklistPrefs
    ) {
        final int oldBuiltInCount = 60;
        final int customCount = checklistPrefs.getInt("custom_count", 0);
        final Map<String, ?> snapshot = new HashMap<>(checklistPrefs.getAll());
        final android.content.SharedPreferences.Editor editor = checklistPrefs.edit();
        final String[] prefixes = {"status_", "notes_", "photo_"};

        for (int destination = 0; destination < CHECKS.length; destination++) {
            if (destination == 5 || destination == 6) {
                for (String prefix : prefixes) editor.remove(prefix + destination);
                continue;
            }
            int source = destination < 5 ? destination : destination - 2;
            for (String prefix : prefixes) {
                copyStoredChecklistValue(editor, snapshot, prefix + source, prefix + destination);
            }
        }

        for (int customIndex = 0; customIndex < customCount; customIndex++) {
            int source = oldBuiltInCount + customIndex;
            int destination = CHECKS.length + customIndex;
            for (String prefix : prefixes) {
                copyStoredChecklistValue(editor, snapshot, prefix + source, prefix + destination);
            }
        }
        editor.apply();
    }

    private void copyStoredChecklistValue(
        android.content.SharedPreferences.Editor editor,
        Map<String, ?> snapshot,
        String source,
        String destination
    ) {
        Object value = snapshot.get(source);
        if (value instanceof Integer) editor.putInt(destination, (Integer) value);
        else if (value instanceof String) editor.putString(destination, (String) value);
        else editor.remove(destination);
    }

    private void reloadActiveChecks() {
        activeChecks.clear();
        Collections.addAll(activeChecks, CHECKS);
        int customCount = prefs.getInt("custom_count", 0);
        for (int i = 0; i < customCount; i++) {
            String customText = prefs.getString("custom_text_" + i, "").trim();
            if (!customText.isEmpty()) activeChecks.add(new CheckItem("Custom Checks", customText));
        }
    }

    private void showLandingPage() {
        rows.clear();
        progress = null;
        showingOrderDetails = false;
        showingArchives = false;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        applySystemBarPadding(root, dp(22), dp(28), dp(22), dp(20));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("TesSure", 30, TESLA_RED, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView badge = text("DELIVERY CHECKLIST", 13, TEXT, true);
        badge.setLetterSpacing(0.12f);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(0, dp(8), 0, dp(8));
        root.addView(badge);

        TextView subtitle = text("Choose your car and get ready for delivery.", 15, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(2), 1.0f);
        root.addView(subtitle);

        Space topSpace = new Space(this);
        root.addView(topSpace, new LinearLayout.LayoutParams(1, dp(20)));

        SwipeViewFlipper modelCarousel = new SwipeViewFlipper(this);
        TextView carouselIndicator = text("", 14, MUTED, true);
        carouselIndicator.setGravity(Gravity.CENTER);

        addModelCard(modelCarousel, carouselIndicator, "Model 3", "model_3", 0);
        addModelCard(modelCarousel, carouselIndicator, "Model Y", "model_y", 1);
        int initialModel = "Model Y".equals(selectedModel()) ? 1 : 0;
        modelCarousel.setDisplayedChild(initialModel);
        updateCarouselIndicator(carouselIndicator, initialModel);
        modelCarousel.setSwipeListener(direction ->
            showCarouselModel(
                modelCarousel,
                carouselIndicator,
                modelCarousel.getDisplayedChild() + direction
            )
        );

        LinearLayout.LayoutParams carouselParams = new LinearLayout.LayoutParams(-1, -2);
        carouselParams.setMargins(0, 0, 0, dp(10));
        root.addView(modelCarousel, carouselParams);

        carouselIndicator.setPadding(0, 0, 0, dp(8));
        root.addView(carouselIndicator);

        if (selectionPrefs.getInt("archive_count", 0) > 0) {
            Button archives = secondaryButton("View Archived Deliveries");
            archives.setOnClickListener(v -> showArchivesPage(false));
            root.addView(archives, fullWidthButtonParams());
        }

        setContentView(scroll);
    }

    private void addOrderSummary(LinearLayout card, String modelName) {
        android.content.SharedPreferences modelPrefs = modelPreferences(modelName);
        String vin = modelPrefs.getString("order_vin", "").trim();
        String eddStart = modelPrefs.getString("order_edd_start", "").trim();
        String eddEnd = modelPrefs.getString("order_edd_end", "").trim();
        String date = modelPrefs.getString("order_collection_date", "").trim();
        String time = modelPrefs.getString("order_collection_time", "").trim();
        String location = storedCollectionLocation(modelPrefs);
        StringBuilder summary = new StringBuilder();
        if (!vin.isEmpty()) summary.append("VIN: ").append(vin);
        if (!eddStart.isEmpty() || !eddEnd.isEmpty()) {
            if (summary.length() > 0) summary.append("\n");
            summary.append("Estimated delivery:");
            if (!eddStart.isEmpty()) summary.append(" ").append(eddStart);
            if (!eddEnd.isEmpty()) summary.append(eddStart.isEmpty() ? " By " : " – ").append(eddEnd);
        }
        if (!date.isEmpty() || !time.isEmpty()) {
            if (summary.length() > 0) summary.append("\n");
            summary.append("Collection:");
            if (!date.isEmpty()) summary.append(" ").append(date);
            if (!time.isEmpty()) summary.append(" at ").append(time);
        }
        if (!location.isEmpty()) {
            if (summary.length() > 0) summary.append("\n");
            summary.append(location);
        }
        boolean hasOrderDetails = summary.length() > 0;
        if (!hasOrderDetails) {
            summary.append("No order details saved for this car.");
        }
        TextView detail = text(summary.toString(), 14, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, dp(10), 0, dp(6));
        detail.setLineSpacing(dp(2), 1.0f);
        card.addView(detail);

        String countdown = deliveryCountdown(modelPrefs);
        if (!countdown.isEmpty()) {
            TextView countdownView = text(countdown, 16, TEXT, true);
            countdownView.setGravity(Gravity.CENTER);
            countdownView.setPadding(dp(12), dp(10), dp(12), dp(10));
            countdownView.setBackground(rounded(SURFACE_2, dp(12), TESLA_RED, 1));
            if (parseOrderDate(modelPrefs.getString("order_collection_date", "")) != null) {
                countdownView.setClickable(true);
                countdownView.setFocusable(true);
                countdownView.setContentDescription(countdown + ". Tap to view live countdown.");
                countdownView.setOnClickListener(v -> showCollectionCountdown(modelPrefs));
            }
            LinearLayout.LayoutParams countdownParams = new LinearLayout.LayoutParams(-1, -2);
            countdownParams.setMargins(0, dp(4), 0, dp(10));
            card.addView(countdownView, countdownParams);
        }

        Button edit = secondaryButton(hasOrderDetails ? "Edit Order Details" : "Add Order Details");
        edit.setOnClickListener(v -> showOrderDetailsPage(false, modelName));
        card.addView(edit, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private void addModelCard(
        ViewFlipper carousel,
        TextView indicator,
        String modelName,
        String drawableName,
        int modelIndex
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(rounded(SURFACE, dp(22), BORDER, 1));
        carousel.addView(card, new ViewGroup.LayoutParams(-1, -2));

        ImageView car = new ImageView(this);
        car.setImageResource(getResources().getIdentifier(drawableName, "drawable", getPackageName()));
        car.setAdjustViewBounds(true);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        car.setContentDescription(modelName + ". Swipe left or right to choose another model.");
        card.addView(car, new LinearLayout.LayoutParams(-1, dp(145)));

        TextView model = text(modelName, 26, TEXT, true);
        model.setGravity(Gravity.CENTER);
        model.setPadding(0, dp(8), 0, 0);
        card.addView(model);

        TextView detail = text("Delivery checks", 14, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        card.addView(detail);

        addOrderSummary(card, modelName);

        android.content.SharedPreferences modelPrefs = modelPreferences(modelName);
        boolean hasProgress = false;
        int modelCheckCount = CHECKS.length + modelPrefs.getInt("custom_count", 0);
        for (int i = 0; i < modelCheckCount && !hasProgress; i++) {
            hasProgress = modelPrefs.contains("status_" + i)
                || modelPrefs.contains("notes_" + i)
                || modelPrefs.contains("photo_" + i);
        }
        Button start = primaryButton((hasProgress ? "Continue " : "Start ") + modelName + " Checklist");
        start.setOnClickListener(v -> selectModel(modelName));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(50));
        buttonParams.setMargins(0, dp(14), 0, 0);
        card.addView(start, buttonParams);
    }

    private void applySystemBarPadding(View view, int left, int top, int right, int bottom) {
        view.setPadding(left, top, right, bottom);
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                left + insets.getSystemWindowInsetLeft(),
                top + insets.getSystemWindowInsetTop(),
                right + insets.getSystemWindowInsetRight(),
                bottom + insets.getSystemWindowInsetBottom()
            );
            return insets;
        });
        view.requestApplyInsets();
    }

    private void showCarouselModel(ViewFlipper carousel, TextView indicator, int target) {
        if (target < 0 || target >= carousel.getChildCount() || target == carousel.getDisplayedChild()) return;

        boolean movingLeft = target > carousel.getDisplayedChild();
        float incomingStart = movingLeft ? 1f : -1f;
        float outgoingEnd = movingLeft ? -1f : 1f;
        android.view.animation.TranslateAnimation inAnimation = new android.view.animation.TranslateAnimation(
            android.view.animation.Animation.RELATIVE_TO_SELF, incomingStart,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0
        );
        android.view.animation.TranslateAnimation outAnimation = new android.view.animation.TranslateAnimation(
            android.view.animation.Animation.RELATIVE_TO_SELF, 0,
            android.view.animation.Animation.RELATIVE_TO_SELF, outgoingEnd,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0
        );
        inAnimation.setDuration(160);
        outAnimation.setDuration(160);
        android.view.animation.DecelerateInterpolator interpolator =
            new android.view.animation.DecelerateInterpolator();
        inAnimation.setInterpolator(interpolator);
        outAnimation.setInterpolator(interpolator);
        carousel.setInAnimation(inAnimation);
        carousel.setOutAnimation(outAnimation);
        carousel.setDisplayedChild(target);
        updateCarouselIndicator(indicator, target);
    }

    private void updateCarouselIndicator(TextView indicator, int modelIndex) {
        indicator.setText((modelIndex == 0 ? "●  ○" : "○  ●") + "   Swipe to select model");
        indicator.setContentDescription((modelIndex + 1) + " of 2. Swipe to select model.");
    }

    private void showChecklistPage() {
        rows.clear();
        showingOrderDetails = false;
        showingArchives = false;
        sectionSpinner = null;
        sectionAdapter = null;
        reloadActiveChecks();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(42), dp(18), dp(14));
        header.setBackgroundColor(Color.rgb(14, 17, 25));
        root.addView(header);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        topRow.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1));

        TextView eyebrow = text(selectedModel().toUpperCase(Locale.UK), 12, TESLA_RED, true);
        eyebrow.setLetterSpacing(0.12f);
        titleBlock.addView(eyebrow);

        TextView title = text("Delivery Checklist", 25, TEXT, true);
        title.setPadding(0, dp(4), 0, dp(2));
        titleBlock.addView(title);

        Button hamburger = secondaryButton("☰");
        hamburger.setTextSize(24);
        hamburger.setOnClickListener(v -> showChecklistMenu(v));
        LinearLayout.LayoutParams hamburgerParams = new LinearLayout.LayoutParams(dp(54), dp(48));
        topRow.addView(hamburger, hamburgerParams);

        progress = text("", 14, MUTED, false);
        progress.setPadding(0, dp(6), 0, 0);
        header.addView(progress);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(activeChecks.size());
        progressBar.setProgress(0);
        LinearLayout.LayoutParams progressBarParams = new LinearLayout.LayoutParams(-1, dp(8));
        progressBarParams.setMargins(0, dp(8), 0, 0);
        header.addView(progressBar, progressBarParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(6), dp(12), dp(92));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        buildChecklist();
        updateProgress();
    }

    private void showChecklistMenu(View anchor) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(14), dp(12), dp(14), dp(12));
        menu.setBackground(rounded(Color.rgb(18, 22, 32), dp(18), BORDER, 1));

        TextView title = text("Checklist menu", 13, TESLA_RED, true);
        title.setLetterSpacing(0.08f);
        title.setPadding(dp(6), 0, dp(6), dp(8));
        menu.addView(title);

        final PopupWindow[] popup = new PopupWindow[1];
        addMenuItem(menu, "Choose Section", "Jump to another checklist section", true, () -> {
            showSectionPicker(menu, popup[0]);
        });
        addMenuItem(menu, "View Issues", "Review issues with Tesla staff", false, () -> {
            popup[0].dismiss();
            showIssues();
        });
        addMenuItem(menu, "Order Details", "VIN and collection information", false, () -> {
            popup[0].dismiss();
            saveAllNotes();
            showOrderDetailsPage(true, selectedModel());
        });
        addMenuItem(menu, "Archived Deliveries", "View previously archived checklists", false, () -> {
            popup[0].dismiss();
            saveAllNotes();
            showArchivesPage(true);
        });
        addMenuItem(menu, "Share Report", "Send the full checklist report", false, () -> {
            popup[0].dismiss();
            shareReport();
        });
        addMenuItem(menu, "Cars", "Return to car selection", false, () -> {
            popup[0].dismiss();
            showLandingPage();
        });
        addMenuItem(menu, "Reset", "Clear checks and notes", false, () -> {
            popup[0].dismiss();
            confirmReset();
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.addView(menu);
        popup[0] = new PopupWindow(scroll, dp(292), WindowManager.LayoutParams.WRAP_CONTENT, true);
        popup[0].setOutsideTouchable(true);
        popup[0].setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) popup[0].setElevation(dp(10));
        popup[0].setAnimationStyle(getResources().getIdentifier("ChecklistPopupAnimation", "style", getPackageName()));
        popup[0].showAsDropDown(anchor, -dp(238), dp(8));
    }

    private void addMenuItem(LinearLayout menu, String label, String subtitle, boolean accent, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(10), dp(12), dp(10));
        item.setClickable(true);
        item.setBackground(rounded(accent ? Color.rgb(43, 33, 42) : SURFACE_2, dp(12), accent ? TESLA_RED : BORDER, 1));
        item.setOnClickListener(v -> action.run());

        TextView labelView = text(label, 16, accent ? Color.WHITE : TEXT, true);
        item.addView(labelView);

        TextView subtitleView = text(subtitle, 12, MUTED, false);
        subtitleView.setPadding(0, dp(2), 0, 0);
        item.addView(subtitleView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        menu.addView(item, params);
    }

    private void showSectionPicker(LinearLayout menu, PopupWindow popup) {
        if (sectionNames.isEmpty()) return;

        menu.removeAllViews();

        TextView title = text("Choose section", 13, TESLA_RED, true);
        title.setLetterSpacing(0.08f);
        title.setPadding(dp(6), 0, dp(6), dp(8));
        menu.addView(title);

        for (String section : sectionNames) {
            int issues = sectionIssueCount(section);
            String subtitle = issues > 0
                ? issues + (issues == 1 ? " issue to review" : " issues to review")
                : sectionComplete(section) ? "Section complete" : "Open checklist section";
            addMenuItem(menu, section, subtitle, section.equals(openSection), () -> {
                popup.dismiss();
                setOpenSection(section);
            });
        }

        int maxHeight = Math.min(dp(600), getResources().getDisplayMetrics().heightPixels - dp(130));
        android.view.animation.AlphaAnimation fadeIn = new android.view.animation.AlphaAnimation(0f, 1f);
        fadeIn.setDuration(160);
        popup.update(dp(292), maxHeight);
        menu.startAnimation(fadeIn);
    }

    private void showOrderDetailsPage(boolean fromChecklist, String model) {
        showingOrderDetails = true;
        orderDetailsOpenedFromChecklist = fromChecklist;
        android.content.SharedPreferences orderPrefs = modelPreferences(model);
        rows.clear();
        progress = null;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(42), dp(20), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView eyebrow = text("DELIVERY INFORMATION", 12, TESLA_RED, true);
        eyebrow.setLetterSpacing(0.12f);
        root.addView(eyebrow);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("Order Details", 28, TEXT, true);
        title.setPadding(0, dp(6), 0, dp(4));
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        Button orderMenu = secondaryButton("☰");
        orderMenu.setContentDescription("Order details menu");
        orderMenu.setTextSize(22);
        titleRow.addView(orderMenu, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView intro = text(
            "Add details from your Tesla app. Saved only on this device.",
            15, MUTED, false
        );
        intro.setLineSpacing(dp(2), 1.0f);
        intro.setPadding(0, 0, 0, dp(12));
        root.addView(intro);

        EditText orderNumber = addOrderField(
            root, "Order number", "Example: RN123456789",
            orderPrefs.getString("order_number", ""),
            android.text.InputType.TYPE_CLASS_TEXT
        );
        EditText vin = addOrderField(
            root, "VIN", "17-character vehicle identification number",
            orderPrefs.getString("order_vin", ""),
            android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        );
        vin.setFilters(new android.text.InputFilter[] {
            new android.text.InputFilter.AllCaps(),
            new android.text.InputFilter.LengthFilter(17)
        });
        TextView eddHeading = text("Estimated delivery window", 16, TESLA_RED, true);
        eddHeading.setPadding(dp(2), dp(4), dp(2), dp(8));
        root.addView(eddHeading);
        EditText eddStart = addOrderField(
            root, "EDD start date", "First estimated delivery date",
            orderPrefs.getString("order_edd_start", ""),
            android.text.InputType.TYPE_CLASS_TEXT
        );
        makeDatePickerField(eddStart);
        EditText eddEnd = addOrderField(
            root, "EDD end date", "Last estimated delivery date",
            orderPrefs.getString("order_edd_end", ""),
            android.text.InputType.TYPE_CLASS_TEXT
        );
        makeDatePickerField(eddEnd);
        TextView collectionHeading = text("Collection appointment", 16, TESLA_RED, true);
        collectionHeading.setPadding(dp(2), dp(4), dp(2), dp(8));
        root.addView(collectionHeading);
        EditText collectionDate = addOrderField(
            root, "Collection date", "Example: 14 August 2026",
            orderPrefs.getString("order_collection_date", ""),
            android.text.InputType.TYPE_CLASS_TEXT
        );
        makeDatePickerField(collectionDate);
        EditText collectionTime = addOrderField(
            root, "Collection time", "Example: 10:30",
            orderPrefs.getString("order_collection_time", ""),
            android.text.InputType.TYPE_CLASS_DATETIME
                | android.text.InputType.TYPE_DATETIME_VARIATION_TIME
        );
        makeTimePickerField(collectionTime);
        CollectionLocationSelection collectionLocation =
            addCollectionLocationSelector(root, orderPrefs);

        TextView vinHint = text(
            "The saved VIN appears with the collection-day VIN check.",
            13, MUTED, false
        );
        vinHint.setPadding(dp(2), 0, dp(2), dp(8));
        root.addView(vinHint);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = secondaryButton("Cancel");
        Button save = primaryButton("Save Details");
        buttons.addView(cancel, weightParams());
        buttons.addView(save, weightParams());
        LinearLayout.LayoutParams buttonRowParams = new LinearLayout.LayoutParams(-1, dp(48));
        buttonRowParams.setMargins(0, dp(6), 0, 0);
        root.addView(buttons, buttonRowParams);

        Runnable archiveAction = () -> {
            if (!validOrderVin(vin)) return;
            if (!validDeliveryDates(eddStart, eddEnd)) return;
            new AlertDialog.Builder(this)
                .setTitle("Archive this delivery?")
                .setMessage("A read-only copy of the " + model + " order and checklist will be saved. The active order, checklist progress, issues, photos, and custom checks will then be cleared.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Archive & Start New", (dialog, which) -> {
                    saveOrderFields(orderPrefs, orderNumber, vin, eddStart, eddEnd, collectionDate, collectionTime, collectionLocation);
                    archiveAndResetModel(model);
                })
                .show();
        };

        Runnable clearAction = () -> new AlertDialog.Builder(this)
            .setTitle("Clear order details?")
            .setMessage("This removes the saved order number, VIN, delivery window, collection date, time, and location for " + model + ". Checklist progress will not be changed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear", (dialog, which) -> {
                clearOrderDetails(orderPrefs);
                Toast.makeText(this, "Order details cleared", Toast.LENGTH_SHORT).show();
                showOrderDetailsPage(fromChecklist, model);
            })
            .show();
        orderMenu.setOnClickListener(v ->
            showOrderDetailsMenu(orderMenu, archiveAction, clearAction)
        );

        cancel.setOnClickListener(v -> leaveOrderDetails());
        save.setOnClickListener(v -> {
            if (!validOrderVin(vin)) return;
            if (!validDeliveryDates(eddStart, eddEnd)) return;
            saveOrderFields(orderPrefs, orderNumber, vin, eddStart, eddEnd, collectionDate, collectionTime, collectionLocation);
            Toast.makeText(this, "Order details saved", Toast.LENGTH_SHORT).show();
            leaveOrderDetails();
        });

        setContentView(scroll);
    }

    private void showOrderDetailsMenu(
        View anchor,
        Runnable archiveAction,
        Runnable clearAction
    ) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(14), dp(12), dp(14), dp(4));
        menu.setBackground(rounded(SURFACE, dp(16), BORDER, 1));

        TextView title = text("ORDER ACTIONS", 12, TESLA_RED, true);
        title.setLetterSpacing(0.08f);
        title.setPadding(dp(6), 0, dp(6), dp(8));
        menu.addView(title);

        final PopupWindow[] popup = new PopupWindow[1];
        addMenuItem(
            menu,
            "Archive & Start New",
            "Save a read-only copy and reset this delivery",
            false,
            () -> {
                popup[0].dismiss();
                archiveAction.run();
            }
        );
        addMenuItem(
            menu,
            "Clear Order Details",
            "Remove saved order information",
            true,
            () -> {
                popup[0].dismiss();
                clearAction.run();
            }
        );

        popup[0] = new PopupWindow(menu, dp(292), WindowManager.LayoutParams.WRAP_CONTENT, true);
        popup[0].setOutsideTouchable(true);
        popup[0].setBackgroundDrawable(
            new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popup[0].setElevation(dp(10));
        }
        popup[0].showAsDropDown(anchor, -dp(240), dp(6));
    }

    private CollectionLocationSelection addCollectionLocationSelector(
        LinearLayout root,
        android.content.SharedPreferences orderPrefs
    ) {
        CollectionLocationSelection selection = new CollectionLocationSelection();
        selection.selectedId = orderPrefs.getString("order_collection_location_id", "");

        TextView label = text("Collection location", 14, TEXT, true);
        label.setPadding(dp(2), 0, dp(2), dp(6));
        root.addView(label);

        selection.selector = secondaryButton("Choose collection location");
        selection.selector.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        selection.selector.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams selectorParams = new LinearLayout.LayoutParams(-1, dp(52));
        selectorParams.setMargins(0, 0, 0, dp(6));
        root.addView(selection.selector, selectorParams);

        updateCollectionLocationSelection(selection);
        selection.selector.setOnClickListener(v -> showCollectionLocationPicker(selection));
        return selection;
    }

    private void showCollectionLocationPicker(CollectionLocationSelection selection) {
        final Dialog dialog = new Dialog(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(12));
        content.setBackgroundColor(BG);

        TextView title = text("Choose collection location", 22, TEXT, true);
        title.setPadding(0, 0, 0, dp(12));
        content.addView(title);

        EditText search = new EditText(this);
        search.setHint("Search name, city, postcode or country");
        search.setHintTextColor(MUTED);
        search.setTextColor(TEXT);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(rounded(SURFACE_2, dp(12), BORDER, 1));
        content.addView(search, new LinearLayout.LayoutParams(-1, dp(50)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, dp(12), 0, dp(8));
        scroll.addView(results);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        content.addView(scroll, scrollParams);

        Runnable render = () -> renderCollectionLocationResults(
            results, search.getText().toString(), selection, dialog
        );
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                render.run();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        render.run();

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            );
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(ignored -> {
            Window shownWindow = dialog.getWindow();
            if (shownWindow != null) shownWindow.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            );
        });
        dialog.show();
    }

    private void renderCollectionLocationResults(
        LinearLayout results,
        String query,
        CollectionLocationSelection selection,
        Dialog dialog
    ) {
        results.removeAllViews();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        LinkedHashMap<String, ArrayList<TeslaCollectionLocation>> groups = new LinkedHashMap<>();
        groups.put("United Kingdom", new ArrayList<>());
        groups.put("Northern Ireland", new ArrayList<>());
        groups.put("Republic of Ireland", new ArrayList<>());
        groups.put("European Union", new ArrayList<>());

        for (TeslaCollectionLocation location : locationRepository.getAllLocationsNow()) {
            String searchable = (
                location.getLocationName() + " " + location.getCity() + " "
                    + location.getPostcode() + " " + location.getCountryName()
            ).toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && !searchable.contains(needle)) continue;
            String group;
            if ("Northern Ireland".equals(location.getRegion())) group = "Northern Ireland";
            else if ("GB".equals(location.getCountryCode())) group = "United Kingdom";
            else if ("IE".equals(location.getCountryCode())) group = "Republic of Ireland";
            else group = "European Union";
            groups.get(group).add(location);
        }

        Comparator<TeslaCollectionLocation> locationOrder =
            Comparator.comparing(
                TeslaCollectionLocation::getCountryName,
                String.CASE_INSENSITIVE_ORDER
            ).thenComparing(
                TeslaCollectionLocation::getCity,
                String.CASE_INSENSITIVE_ORDER
            ).thenComparing(
                TeslaCollectionLocation::getLocationName,
                String.CASE_INSENSITIVE_ORDER
            );
        for (ArrayList<TeslaCollectionLocation> locations : groups.values()) {
            locations.sort(locationOrder);
        }

        int resultCount = 0;
        for (Map.Entry<String, ArrayList<TeslaCollectionLocation>> entry : groups.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            TextView heading = text(entry.getKey().toUpperCase(Locale.ROOT), 12, TESLA_RED, true);
            heading.setLetterSpacing(0.08f);
            heading.setPadding(dp(4), dp(8), dp(4), dp(7));
            results.addView(heading);
            for (TeslaCollectionLocation location : entry.getValue()) {
                resultCount++;
                TextView item = text(
                    location.getLocationName() + "\n"
                        + location.getCity() + "  ·  " + location.getPostcode() + "\n"
                        + location.getCountryName(),
                    14, TEXT, false
                );
                item.setLineSpacing(dp(2), 1f);
                item.setPadding(dp(14), dp(11), dp(14), dp(11));
                item.setBackground(rounded(SURFACE_2, dp(12), BORDER, 1));
                item.setClickable(true);
                item.setOnClickListener(v -> {
                    selection.selectedId = location.getId();
                    updateCollectionLocationSelection(selection);
                    dialog.dismiss();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(0, 0, 0, dp(8));
                results.addView(item, params);
            }
        }

        if (resultCount == 0) {
            TextView empty = text("No matching collection locations", 14, MUTED, false);
            empty.setPadding(dp(4), dp(14), dp(4), dp(14));
            results.addView(empty);
        }

        Button custom = secondaryButton("My collection point is not listed");
        custom.setOnClickListener(v -> {
            selection.selectedId = "custom";
            updateCollectionLocationSelection(selection);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(-1, dp(50));
        customParams.setMargins(0, dp(8), 0, dp(8));
        results.addView(custom, customParams);
    }

    private void updateCollectionLocationSelection(CollectionLocationSelection selection) {
        if ("custom".equals(selection.selectedId)) {
            selection.selector.setText("My collection point is not listed");
            return;
        }
        TeslaCollectionLocation location = findCollectionLocation(selection.selectedId);
        if (location == null) {
            selection.selector.setText("Choose collection location");
        } else {
            selection.selector.setText(location.getCity());
        }
    }

    private TeslaCollectionLocation findCollectionLocation(String id) {
        if (id == null || id.isEmpty() || "custom".equals(id)) return null;
        for (TeslaCollectionLocation location : locationRepository.getAllLocationsNow()) {
            if (id.equals(location.getId())) return location;
        }
        return null;
    }

    private String storedCollectionLocation(android.content.SharedPreferences orderPrefs) {
        String id = orderPrefs.getString("order_collection_location_id", "");
        TeslaCollectionLocation location = findCollectionLocation(id);
        if (location != null) return location.formattedAddress();
        if ("custom".equals(id)) {
            return "My collection point is not listed";
        }
        return orderPrefs.getString("order_collection_location", "").trim();
    }

    private boolean validOrderVin(EditText vin) {
        String enteredVin = vin.getText().toString().trim().toUpperCase(Locale.UK);
        if (!enteredVin.isEmpty() && !enteredVin.matches("[A-HJ-NPR-Z0-9]{17}")) {
            vin.setError("Enter a valid 17-character VIN");
            vin.requestFocus();
            return false;
        }
        return true;
    }

    private boolean validDeliveryDates(EditText eddStart, EditText eddEnd) {
        String startText = eddStart.getText().toString().trim();
        String endText = eddEnd.getText().toString().trim();
        if (startText.isEmpty() && endText.isEmpty()) return true;
        if (startText.isEmpty()) {
            eddStart.setError("Choose the first estimated delivery date");
            eddStart.requestFocus();
            return false;
        }
        if (endText.isEmpty()) {
            eddEnd.setError("Choose the last estimated delivery date");
            eddEnd.requestFocus();
            return false;
        }
        Date start = parseOrderDate(startText);
        Date end = parseOrderDate(endText);
        if (start == null || end == null || end.before(start)) {
            eddEnd.setError("End date must be on or after the start date");
            eddEnd.requestFocus();
            return false;
        }
        return true;
    }

    private Date parseOrderDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        SimpleDateFormat format = new SimpleDateFormat("dd MMMM yyyy", Locale.UK);
        format.setLenient(false);
        try {
            return format.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private long daysFromToday(Date target) {
        java.time.LocalDate targetDay = target.toInstant()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate();
        return java.time.temporal.ChronoUnit.DAYS.between(
            java.time.LocalDate.now(), targetDay
        );
    }

    private String deliveryCountdown(android.content.SharedPreferences orderPrefs) {
        Date collection = parseOrderDate(orderPrefs.getString("order_collection_date", ""));
        if (collection != null) {
            long days = daysFromToday(collection);
            if (days == 0) return "Collection day is today";
            if (days > 0) return "Collection in " + days + " day" + (days == 1 ? "" : "s");
            long elapsed = Math.abs(days);
            return "Collection was " + elapsed + " day" + (elapsed == 1 ? "" : "s") + " ago";
        }

        Date start = parseOrderDate(orderPrefs.getString("order_edd_start", ""));
        if (start == null) return "";
        long days = daysFromToday(start);
        if (days == 0) return "Estimated delivery starts today";
        if (days > 0) return "Estimated delivery starts in " + days + " day" + (days == 1 ? "" : "s");

        Date end = parseOrderDate(orderPrefs.getString("order_edd_end", ""));
        if (end != null) {
            long daysToEnd = daysFromToday(end);
            if (daysToEnd == 0) return "Estimated delivery window ends today";
            if (daysToEnd > 0) return "In estimated delivery window · " + daysToEnd
                + " day" + (daysToEnd == 1 ? "" : "s") + " remaining";
        }
        return "Estimated delivery window has passed";
    }

    private Date collectionDateTime(android.content.SharedPreferences orderPrefs) {
        String date = orderPrefs.getString("order_collection_date", "").trim();
        if (date.isEmpty()) return null;
        String time = orderPrefs.getString("order_collection_time", "").trim();
        SimpleDateFormat format = new SimpleDateFormat(
            time.isEmpty() ? "dd MMMM yyyy" : "dd MMMM yyyy HH:mm",
            Locale.UK
        );
        format.setLenient(false);
        try {
            return format.parse(time.isEmpty() ? date : date + " " + time);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showCollectionCountdown(android.content.SharedPreferences orderPrefs) {
        Date collection = collectionDateTime(orderPrefs);
        if (collection == null) return;
        boolean hasTime = !orderPrefs.getString("order_collection_time", "").trim().isEmpty();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(12), dp(24), dp(8));
        TextView timer = text("", 28, TEXT, true);
        timer.setGravity(Gravity.CENTER);
        timer.setPadding(0, dp(12), 0, dp(12));
        content.addView(timer, new LinearLayout.LayoutParams(-1, -2));
        TextView dateLabel = text(
            "Collection: " + orderPrefs.getString("order_collection_date", "")
                + (hasTime ? " at " + orderPrefs.getString("order_collection_time", "") : ""),
            15, MUTED, false
        );
        dateLabel.setGravity(Gravity.CENTER);
        content.addView(dateLabel);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Collection Countdown")
            .setView(content)
            .setPositiveButton("Close", null)
            .create();
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable updater = new Runnable() {
            @Override public void run() {
                long remaining = collection.getTime() - System.currentTimeMillis();
                if (!hasTime && daysFromToday(collection) == 0) {
                    timer.setText("Collection day is today");
                } else if (remaining <= 0) {
                    timer.setText(hasTime ? "Collection time has arrived" : "Collection day has passed");
                } else {
                    long totalSeconds = remaining / 1000;
                    long days = totalSeconds / 86400;
                    long hours = (totalSeconds % 86400) / 3600;
                    long minutes = (totalSeconds % 3600) / 60;
                    long seconds = totalSeconds % 60;
                    timer.setText(String.format(
                        Locale.UK, "%d days  %02d:%02d:%02d", days, hours, minutes, seconds
                    ));
                    handler.postDelayed(this, 1000);
                }
            }
        };
        dialog.setOnShowListener(ignored -> updater.run());
        dialog.setOnDismissListener(ignored -> handler.removeCallbacks(updater));
        dialog.show();
    }

    private void saveOrderFields(
        android.content.SharedPreferences orderPrefs,
        EditText orderNumber,
        EditText vin,
        EditText eddStart,
        EditText eddEnd,
        EditText collectionDate,
        EditText collectionTime,
        CollectionLocationSelection collectionLocation
    ) {
        android.content.SharedPreferences.Editor editor = orderPrefs.edit()
            .putString("order_number", orderNumber.getText().toString().trim())
            .putString("order_vin", vin.getText().toString().trim().toUpperCase(Locale.UK))
            .putString("order_edd_start", eddStart.getText().toString().trim())
            .putString("order_edd_end", eddEnd.getText().toString().trim())
            .putString("order_collection_date", collectionDate.getText().toString().trim())
            .putString("order_collection_time", collectionTime.getText().toString().trim())
            .remove("order_collection_location");
        if (collectionLocation.selectedId == null || collectionLocation.selectedId.isEmpty()) {
            editor.remove("order_collection_location_id");
        } else {
            editor.putString("order_collection_location_id", collectionLocation.selectedId);
        }
        editor
            .remove("order_collection_custom_name")
            .remove("order_collection_custom_address")
            .remove("order_collection_custom_city")
            .remove("order_collection_custom_postcode")
            .remove("order_collection_custom_country");
        editor.apply();
    }

    private void clearOrderDetails(android.content.SharedPreferences orderPrefs) {
        orderPrefs.edit()
            .remove("order_number")
            .remove("order_vin")
            .remove("order_edd_start")
            .remove("order_edd_end")
            .remove("order_collection_date")
            .remove("order_collection_time")
            .remove("order_collection_location")
            .remove("order_collection_location_id")
            .remove("order_collection_custom_name")
            .remove("order_collection_custom_address")
            .remove("order_collection_custom_city")
            .remove("order_collection_custom_postcode")
            .remove("order_collection_custom_country")
            .apply();
    }

    private EditText addOrderField(
        LinearLayout root,
        String label,
        String hint,
        String value,
        int inputType
    ) {
        TextView fieldLabel = text(label, 14, TEXT, true);
        fieldLabel.setPadding(dp(2), 0, dp(2), dp(6));
        root.addView(fieldLabel);

        EditText input = new EditText(this);
        input.setText(value);
        input.setHint(hint);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(16);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(rounded(SURFACE, dp(12), BORDER, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(14));
        root.addView(input, params);
        return input;
    }

    private void makeDatePickerField(EditText input) {
        input.setFocusable(false);
        input.setClickable(true);
        input.setOnClickListener(v -> {
            Calendar selected = Calendar.getInstance();
            String current = input.getText().toString().trim();
            if (!current.isEmpty()) {
                try {
                    Date parsed = new SimpleDateFormat("dd MMMM yyyy", Locale.UK).parse(current);
                    if (parsed != null) selected.setTime(parsed);
                } catch (Exception ignored) { }
            }
            DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    Calendar value = Calendar.getInstance();
                    value.set(year, month, day);
                    input.setText(new SimpleDateFormat("dd MMMM yyyy", Locale.UK).format(value.getTime()));
                },
                selected.get(Calendar.YEAR),
                selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH)
            );
            picker.show();
        });
    }

    private void makeTimePickerField(EditText input) {
        input.setFocusable(false);
        input.setClickable(true);
        input.setOnClickListener(v -> {
            Calendar selected = Calendar.getInstance();
            String current = input.getText().toString().trim();
            if (!current.isEmpty()) {
                try {
                    Date parsed = new SimpleDateFormat("HH:mm", Locale.UK).parse(current);
                    if (parsed != null) selected.setTime(parsed);
                } catch (Exception ignored) { }
            }
            TimePickerDialog picker = new TimePickerDialog(
                this,
                (view, hour, minute) -> input.setText(String.format(Locale.UK, "%02d:%02d", hour, minute)),
                selected.get(Calendar.HOUR_OF_DAY),
                selected.get(Calendar.MINUTE),
                true
            );
            picker.show();
        });
    }

    private void leaveOrderDetails() {
        boolean returnToChecklist = orderDetailsOpenedFromChecklist;
        showingOrderDetails = false;
        if (returnToChecklist) showChecklistPage();
        else showLandingPage();
    }

    private void archiveAndResetModel(String model) {
        android.content.SharedPreferences modelPrefs = modelPreferences(model);
        String report = buildArchivedReport(model, modelPrefs);
        String vin = modelPrefs.getString("order_vin", "").trim();
        String orderNumber = modelPrefs.getString("order_number", "").trim();
        String date = modelPrefs.getString("order_collection_date", "").trim();
        String reference = !vin.isEmpty() ? "VIN …" + vin.substring(Math.max(0, vin.length() - 6))
            : !orderNumber.isEmpty() ? orderNumber : "Delivery";
        String title = model + " — " + reference;
        if (!date.isEmpty()) title += " — " + date;

        int archiveIndex = selectionPrefs.getInt("archive_count", 0);
        selectionPrefs.edit()
            .putString("archive_title_" + archiveIndex, title)
            .putString("archive_report_" + archiveIndex, report)
            .putLong("archive_created_" + archiveIndex, System.currentTimeMillis())
            .putInt("archive_count", archiveIndex + 1)
            .apply();

        modelPrefs.edit().clear().apply();
        Toast.makeText(this, "Delivery archived", Toast.LENGTH_SHORT).show();
        showArchivesPage(orderDetailsOpenedFromChecklist);
    }

    private String buildArchivedReport(String model, android.content.SharedPreferences modelPrefs) {
        ArrayList<CheckItem> snapshotChecks = new ArrayList<>();
        Collections.addAll(snapshotChecks, CHECKS);
        int customCount = modelPrefs.getInt("custom_count", 0);
        for (int i = 0; i < customCount; i++) {
            String customText = modelPrefs.getString("custom_text_" + i, "").trim();
            if (!customText.isEmpty()) snapshotChecks.add(new CheckItem("Custom Checks", customText));
        }

        StringBuilder report = new StringBuilder();
        report.append("Archived Tesla ").append(model).append(" Delivery Checklist\n");
        report.append(new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK).format(new Date())).append("\n\n");
        appendOrderDetailsToReport(report, modelPrefs);
        String currentSection = "";
        for (int i = 0; i < snapshotChecks.size(); i++) {
            CheckItem item = snapshotChecks.get(i);
            if (!item.section.equals(currentSection)) {
                currentSection = item.section;
                report.append("\n").append(currentSection).append("\n");
            }
            int statusValue = modelPrefs.getInt("status_" + i, -1);
            String status = statusValue == 0 ? "PASS" : statusValue == 1 ? "ISSUE"
                : statusValue == 2 ? "N/A" : "NOT CHECKED";
            report.append("- [").append(status).append("] ").append(item.text);
            String note = modelPrefs.getString("notes_" + i, "").trim();
            if (statusValue == 1 && !note.isEmpty()) report.append(" — ").append(note);
            if (statusValue == 1 && !modelPrefs.getString("photo_" + i, "").trim().isEmpty()) {
                report.append(" — Photo was attached");
            }
            report.append("\n");
        }
        return report.toString();
    }

    private void showArchivesPage(boolean fromChecklist) {
        showingOrderDetails = false;
        showingArchives = true;
        archivesOpenedFromChecklist = fromChecklist;
        rows.clear();
        progress = null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(42), dp(20), dp(34));
        root.setBackgroundColor(BG);

        TextView eyebrow = text("DELIVERY HISTORY", 12, TESLA_RED, true);
        eyebrow.setLetterSpacing(0.12f);
        root.addView(eyebrow);
        TextView title = text("Archived Deliveries", 28, TEXT, true);
        title.setPadding(0, dp(6), 0, dp(4));
        root.addView(title);
        TextView intro = text("Archived checklists are read-only snapshots stored on this device.", 14, MUTED, false);
        intro.setPadding(0, 0, 0, dp(14));
        root.addView(intro);

        Button back = secondaryButton(fromChecklist ? "Back to Checklist" : "Back to Cars");
        back.setOnClickListener(v -> leaveArchives());
        root.addView(back, fullWidthButtonParams());

        ScrollView scroll = new ScrollView(this);
        LinearLayout archiveList = new LinearLayout(this);
        archiveList.setOrientation(LinearLayout.VERTICAL);
        archiveList.setPadding(0, dp(14), 0, dp(30));
        scroll.addView(archiveList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        int count = selectionPrefs.getInt("archive_count", 0);
        if (count == 0) {
            TextView empty = text("No deliveries have been archived yet.", 16, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            archiveList.addView(empty);
        }
        for (int index = count - 1; index >= 0; index--) {
            final int archiveIndex = index;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackground(rounded(SURFACE, dp(14), BORDER, 1));

            String archiveTitle = selectionPrefs.getString("archive_title_" + index, "Archived delivery");
            card.addView(text(archiveTitle, 17, TEXT, true));
            long created = selectionPrefs.getLong("archive_created_" + index, 0);
            if (created > 0) {
                TextView createdText = text(
                    "Archived " + new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK).format(new Date(created)),
                    13, MUTED, false
                );
                createdText.setPadding(0, dp(3), 0, dp(9));
                card.addView(createdText);
            }
            Button view = secondaryButton("View or Share Checklist");
            view.setOnClickListener(v -> showArchivedReport(archiveIndex));
            card.addView(view, new LinearLayout.LayoutParams(-1, dp(46)));

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.setMargins(0, 0, 0, dp(12));
            archiveList.addView(card, cardParams);
        }
        setContentView(root);
    }

    private void showArchivedReport(int archiveIndex) {
        String title = selectionPrefs.getString("archive_title_" + archiveIndex, "Archived delivery");
        String report = selectionPrefs.getString("archive_report_" + archiveIndex, "");
        TextView reportView = text(report, 14, TEXT, false);
        reportView.setTextIsSelectable(true);
        reportView.setPadding(dp(18), dp(8), dp(18), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(reportView);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton("Close", null)
            .setPositiveButton("Share", null)
            .create();
        dialog.setOnShowListener(ignored ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> shareArchivedReport(title, report))
        );
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.82f);
            window.setAttributes(params);
        }
    }

    private void shareArchivedReport(String title, String report) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, title);
        send.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(send, "Share archived checklist"));
    }

    private void leaveArchives() {
        boolean returnToChecklist = archivesOpenedFromChecklist;
        showingArchives = false;
        if (returnToChecklist) showChecklistPage();
        else showLandingPage();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button primaryButton(String text) {
        Button b = baseButton(text);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(TESLA_RED, dp(14), TESLA_RED, 0));
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = baseButton(text);
        b.setTextColor(TEXT);
        b.setBackground(rounded(SURFACE_2, dp(14), BORDER, 1));
        return b;
    }

    private Button makeSmallButton(String text, View.OnClickListener l) {
        Button b = secondaryButton(text);
        b.setTextSize(13);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setOnClickListener(l);
        return b;
    }

    private Button baseButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(48));
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private LinearLayout.LayoutParams fullWidthButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(54));
        p.setMargins(0, dp(8), 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidthDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidthDp > 0) d.setStroke(dp(strokeWidthDp), strokeColor);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void buildChecklist() {
        sectionNames.clear();
        sectionContentViews.clear();
        openSection = null;

        for (int i = 0; i < activeChecks.size(); i++) {
            CheckItem item = activeChecks.get(i);
            LinearLayout currentContent = sectionContentViews.get(item.section);
            if (currentContent == null) {
                sectionNames.add(item.section);

                currentContent = new LinearLayout(this);
                currentContent.setOrientation(LinearLayout.VERTICAL);
                currentContent.setPadding(0, dp(8), 0, 0);
                list.addView(currentContent);

                TextView sectionTitle = text(item.section, 22, TEXT, true);
                sectionTitle.setPadding(dp(4), dp(4), dp(4), dp(8));
                currentContent.addView(sectionTitle);

                TextView sectionHint = text("Complete each item below. If you select Issue, an issue note field will appear.", 14, MUTED, false);
                sectionHint.setPadding(dp(4), 0, dp(4), dp(12));
                currentContent.addView(sectionHint);

                sectionContentViews.put(item.section, currentContent);
            }
            addItemRow(i, item, currentContent);
        }

        LinearLayout customContent = sectionContentViews.get("Custom Checks");
        if (customContent == null) {
            sectionNames.add("Custom Checks");
            customContent = new LinearLayout(this);
            customContent.setOrientation(LinearLayout.VERTICAL);
            customContent.setPadding(0, dp(8), 0, 0);
            list.addView(customContent);

            TextView sectionTitle = text("Custom Checks", 22, TEXT, true);
            sectionTitle.setPadding(dp(4), dp(4), dp(4), dp(8));
            customContent.addView(sectionTitle);

            TextView sectionHint = text("Add anything you want to inspect that is not covered elsewhere.", 14, MUTED, false);
            sectionHint.setPadding(dp(4), 0, dp(4), dp(12));
            customContent.addView(sectionHint);
            sectionContentViews.put("Custom Checks", customContent);
        }

        Button addCustomCheck = primaryButton("+ Add Custom Check");
        addCustomCheck.setOnClickListener(v -> showAddCustomCheckDialog());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, dp(52));
        addParams.setMargins(0, dp(4), 0, dp(12));
        customContent.addView(addCustomCheck, addParams);

        setupSectionDropdown();
        setOpenSection(sectionNames.isEmpty() ? null : sectionNames.get(0));
    }

    private void setupSectionDropdown() {
        if (sectionSpinner == null) return;
        sectionAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, sectionNames) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                styleSectionSpinnerText(view, position, false);
                return view;
            }

            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                styleSectionSpinnerText(view, position, true);
                return view;
            }
        };
        sectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sectionSpinner.setAdapter(sectionAdapter);
        sectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!updatingSectionSpinner && position >= 0 && position < sectionNames.size()) {
                    setOpenSection(sectionNames.get(position));
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void showAddCustomCheckDialog() {
        final EditText input = new EditText(this);
        input.setHint("What would you like to check?");
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Add Custom Check")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String checkText = input.getText().toString().trim();
            if (checkText.isEmpty()) {
                input.setError("Enter a check");
                return;
            }
            int customIndex = prefs.getInt("custom_count", 0);
            prefs.edit()
                .putString("custom_text_" + customIndex, checkText)
                .putInt("custom_count", customIndex + 1)
                .apply();
            dialog.dismiss();
            showChecklistPage();
            setOpenSection("Custom Checks");
        }));
        dialog.getWindow();
        dialog.show();
    }

    private void confirmRemoveCustomCheck(int customIndex, String checkText) {
        new AlertDialog.Builder(this)
            .setTitle("Remove custom check?")
            .setMessage(checkText)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove", (dialog, which) -> removeCustomCheck(customIndex))
            .show();
    }

    private void removeCustomCheck(int customIndex) {
        int customCount = prefs.getInt("custom_count", 0);
        if (customIndex < 0 || customIndex >= customCount) return;

        android.content.SharedPreferences.Editor editor = prefs.edit();
        for (int i = customIndex; i < customCount - 1; i++) {
            editor.putString("custom_text_" + i, prefs.getString("custom_text_" + (i + 1), ""));
            copyCustomValue(editor, "status_", CHECKS.length + i + 1, CHECKS.length + i);
            copyCustomValue(editor, "notes_", CHECKS.length + i + 1, CHECKS.length + i);
            copyCustomValue(editor, "photo_", CHECKS.length + i + 1, CHECKS.length + i);
        }
        int lastIndex = customCount - 1;
        editor.remove("custom_text_" + lastIndex);
        editor.remove("status_" + (CHECKS.length + lastIndex));
        editor.remove("notes_" + (CHECKS.length + lastIndex));
        editor.remove("photo_" + (CHECKS.length + lastIndex));
        editor.putInt("custom_count", lastIndex);
        editor.apply();
        showChecklistPage();
        setOpenSection("Custom Checks");
    }

    private void copyCustomValue(
        android.content.SharedPreferences.Editor editor,
        String prefix,
        int sourceIndex,
        int destinationIndex
    ) {
        String source = prefix + sourceIndex;
        String destination = prefix + destinationIndex;
        if (!prefs.contains(source)) {
            editor.remove(destination);
        } else if ("status_".equals(prefix)) {
            editor.putInt(destination, prefs.getInt(source, -1));
        } else {
            editor.putString(destination, prefs.getString(source, ""));
        }
    }

    private void styleSectionSpinnerText(TextView view, int position, boolean dropdown) {
        String section = position >= 0 && position < sectionNames.size() ? sectionNames.get(position) : "";
        view.setText(sectionDropdownLabel(section));
        view.setTextSize(16);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(TEXT);
        view.setPadding(dp(dropdown ? 16 : 12), dropdown ? dp(14) : 0, dp(12), dropdown ? dp(14) : 0);
        if (dropdown) view.setBackgroundColor(SURFACE_2);
    }

    private CharSequence sectionDropdownLabel(String section) {
        int issues = sectionIssueCount(section);
        boolean complete = sectionComplete(section);
        if (issues > 0) {
            String label = "● " + issues + "  " + section;
            SpannableString span = new SpannableString(label);
            span.setSpan(new ForegroundColorSpan(TESLA_RED), 0, Math.min(label.length(), 3 + String.valueOf(issues).length()), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return span;
        }
        if (complete) {
            String label = "✓  " + section;
            SpannableString span = new SpannableString(label);
            span.setSpan(new ForegroundColorSpan(Color.rgb(116, 242, 160)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return span;
        }
        return section;
    }

    private void refreshSectionDropdown() {
        if (sectionAdapter != null) sectionAdapter.notifyDataSetChanged();
    }

    private void addItemRow(final int index, CheckItem item, LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(SURFACE, dp(16), BORDER, 1));
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dp(12));
        parent.addView(card, cp);

        TextView question = text(item.text, 16, TEXT, false);
        card.addView(question);

        if (index == 6) addExpectedVin(card);

        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rg.setPadding(0, dp(6), 0, 0);
        String[] labels = {"Pass", "Issue", "N/A"};
        for (int j=0; j<labels.length; j++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[j]);
            rb.setTextColor(MUTED);
            rb.setTextSize(14);
            rb.setId(1000 + index * 10 + j);
            rg.addView(rb);
        }
        int savedStatus = prefs.getInt("status_" + index, -1);
        if (savedStatus >= 0) rg.check(1000 + index * 10 + savedStatus);
        card.addView(rg);

        EditText notes = new EditText(this);
        notes.setText(prefs.getString("notes_" + index, ""));
        notes.setVisibility(View.GONE);
        card.addView(notes, new LinearLayout.LayoutParams(1, 1));

        Button editIssue = secondaryButton("Edit issue details");
        editIssue.setTextSize(14);
        editIssue.setVisibility(savedStatus == 1 ? View.VISIBLE : View.GONE);
        editIssue.setOnClickListener(v -> showIssueNoteDialog(index, item, notes));
        LinearLayout.LayoutParams editIssueParams = new LinearLayout.LayoutParams(-1, dp(44));
        editIssueParams.setMargins(0, dp(8), 0, 0);
        card.addView(editIssue, editIssueParams);

        rg.setOnCheckedChangeListener((group, checkedId) -> {
            int status = checkedId - (1000 + index * 10);
            prefs.edit().putInt("status_" + index, status).apply();
            editIssue.setVisibility(status == 1 ? View.VISIBLE : View.GONE);
            updateProgress();
            refreshSectionDropdown();
            if (status == 1) {
                showIssueNoteDialog(index, item, notes);
            } else {
                openNextSectionIfComplete(item.section);
            }
        });
        if (index >= CHECKS.length) {
            Button remove = secondaryButton("Remove custom check");
            remove.setTextSize(14);
            remove.setOnClickListener(v -> confirmRemoveCustomCheck(index - CHECKS.length, item.text));
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(-1, dp(44));
            removeParams.setMargins(0, dp(8), 0, 0);
            card.addView(remove, removeParams);
        }
        rows.add(new ItemRow(index, item, rg, notes));
    }

    private void addExpectedVin(LinearLayout card) {
        String vin = prefs.getString("order_vin", "").trim();
        TextView expected = text(
            vin.isEmpty() ? "No expected VIN saved" : "EXPECTED VIN  " + vin,
            vin.isEmpty() ? 13 : 15,
            vin.isEmpty() ? MUTED : Color.rgb(116, 242, 160),
            true
        );
        expected.setTextIsSelectable(!vin.isEmpty());
        expected.setPadding(dp(12), dp(10), dp(12), dp(10));
        expected.setBackground(rounded(SURFACE_2, dp(10), vin.isEmpty() ? BORDER : Color.rgb(75, 145, 98), 1));
        LinearLayout.LayoutParams expectedParams = new LinearLayout.LayoutParams(-1, -2);
        expectedParams.setMargins(0, dp(10), 0, 0);
        card.addView(expected, expectedParams);

        if (vin.isEmpty()) {
            Button add = secondaryButton("Add expected VIN");
            add.setTextSize(13);
            add.setOnClickListener(v -> showOrderDetailsPage(true, selectedModel()));
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, dp(44));
            addParams.setMargins(0, dp(8), 0, 0);
            card.addView(add, addParams);
        }
    }

    private void setOpenSection(String section) {
        if (section == null && !sectionContentViews.isEmpty()) section = sectionContentViews.keySet().iterator().next();
        openSection = section;
        for (Map.Entry<String, LinearLayout> entry : sectionContentViews.entrySet()) {
            entry.getValue().setVisibility(entry.getKey().equals(openSection) ? View.VISIBLE : View.GONE);
        }
        if (sectionSpinner != null && openSection != null) {
            int index = sectionNames.indexOf(openSection);
            if (index >= 0 && sectionSpinner.getSelectedItemPosition() != index) {
                updatingSectionSpinner = true;
                sectionSpinner.setSelection(index);
                updatingSectionSpinner = false;
            }
        }
        updateProgress();
        refreshSectionDropdown();
    }

    private int sectionIssueCount(String section) {
        int issues = 0;
        for (int i = 0; i < activeChecks.size(); i++) {
            if (activeChecks.get(i).section.equals(section) && prefs.getInt("status_" + i, -1) == 1) issues++;
        }
        return issues;
    }

    private String sectionProgress(String section) {
        int done = 0;
        int total = 0;
        int issues = 0;
        for (int i = 0; i < activeChecks.size(); i++) {
            if (activeChecks.get(i).section.equals(section)) {
                total++;
                int status = prefs.getInt("status_" + i, -1);
                if (status >= 0) done++;
                if (status == 1) issues++;
            }
        }
        return done + " / " + total + " complete" + (issues > 0 ? " • " + issues + " issue(s)" : "");
    }

    private boolean sectionComplete(String section) {
        boolean hasItems = false;
        for (int i = 0; i < activeChecks.size(); i++) {
            if (activeChecks.get(i).section.equals(section)) {
                hasItems = true;
                if (prefs.getInt("status_" + i, -1) < 0) return false;
            }
        }
        return hasItems;
    }

    private String firstIncompleteSection() {
        for (String section : sectionContentViews.keySet()) {
            if (!sectionComplete(section)) return section;
        }
        return sectionContentViews.isEmpty() ? null : sectionContentViews.keySet().iterator().next();
    }

    private void openNextSectionIfComplete(String section) {
        if (!sectionComplete(section)) return;
        boolean next = false;
        for (String candidate : sectionContentViews.keySet()) {
            if (next) {
                setOpenSection(candidate);
                Toast.makeText(this, "Next section: " + candidate, Toast.LENGTH_SHORT).show();
                return;
            }
            if (candidate.equals(section)) next = true;
        }
        setOpenSection(section);
    }

    private void showIssueNoteDialog(int index, CheckItem item, EditText storedNotes) {
        final EditText input = new EditText(this);
        input.setHint("Type the issue found...");
        input.setSingleLine(false);
        input.setMinLines(5);
        input.setTextColor(TEXT);
        input.setHintTextColor(Color.rgb(120, 128, 145));
        input.setText(storedNotes.getText().toString());
        input.setSelection(input.getText().length());
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(rounded(SURFACE_2, dp(12), BORDER, 1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(10), dp(18), 0);

        TextView step = text("Step 1 of 2 — Describe the issue", 15, TESLA_RED, true);
        step.setPadding(0, 0, 0, dp(8));
        content.addView(step);

        TextView itemText = text(item.text, 15, MUTED, false);
        itemText.setPadding(0, 0, 0, dp(12));
        content.addView(itemText);
        content.addView(input, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Issue details")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Next: Add photo", null)
            .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setTextColor(TESLA_RED);
            positive.setOnClickListener(v -> {
                storedNotes.setText(input.getText().toString());
                saveNote(index, storedNotes);
                dialog.dismiss();
                showIssuePhotoDialog(index, item, storedNotes);
            });
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            negative.setTextColor(MUTED);

            input.requestFocus();
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams params = window.getAttributes();
            params.y = dp(24);
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(params);
            window.setBackgroundDrawable(rounded(SURFACE, dp(18), BORDER, 1));
        }
    }

    private void showIssuePhotoDialog(int index, CheckItem item, EditText storedNotes) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(10), dp(18), 0);

        TextView step = text("Step 2 of 2 — Add a photo", 15, TESLA_RED, true);
        step.setPadding(0, 0, 0, dp(8));
        content.addView(step);

        TextView helper = text("Add a photo from your gallery or take one now. You can skip this if a photo is not needed.", 15, MUTED, false);
        helper.setPadding(0, 0, 0, dp(12));
        content.addView(helper);

        ImageView preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(rounded(SURFACE_2, dp(12), BORDER, 1));
        content.addView(preview, new LinearLayout.LayoutParams(-1, dp(190)));
        updateIssuePhotoPreview(preview, prefs.getString("photo_" + index, ""));

        LinearLayout photoButtons = new LinearLayout(this);
        photoButtons.setOrientation(LinearLayout.HORIZONTAL);
        photoButtons.setPadding(0, dp(10), 0, 0);
        Button gallery = secondaryButton("Gallery");
        Button camera = secondaryButton("Camera");
        gallery.setOnClickListener(v -> pickIssuePhoto(index, preview));
        camera.setOnClickListener(v -> takeIssuePhoto(index, preview));
        photoButtons.addView(gallery, weightParams());
        photoButtons.addView(camera, weightParams());
        content.addView(photoButtons);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Issue photo")
            .setView(content)
            .setNegativeButton("Back", null)
            .setNeutralButton("Skip", null)
            .setPositiveButton("Done", null)
            .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setTextColor(TESLA_RED);
            positive.setOnClickListener(v -> {
                refreshSectionDropdown();
                dialog.dismiss();
                openNextSectionIfComplete(item.section);
            });

            Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            neutral.setTextColor(MUTED);
            neutral.setOnClickListener(v -> {
                refreshSectionDropdown();
                dialog.dismiss();
                openNextSectionIfComplete(item.section);
            });

            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            negative.setTextColor(MUTED);
            negative.setOnClickListener(v -> {
                dialog.dismiss();
                showIssueNoteDialog(index, item, storedNotes);
            });
        });

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams params = window.getAttributes();
            params.y = dp(24);
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(params);
            window.setBackgroundDrawable(rounded(SURFACE, dp(18), BORDER, 1));
        }
    }

    private void pickIssuePhoto(int index, ImageView preview) {
        pendingIssuePhotoIndex = index;
        pendingIssuePhotoPreview = preview;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_ISSUE_PHOTO);
    }

    private void takeIssuePhoto(int index, ImageView preview) {
        pendingIssuePhotoIndex = index;
        pendingIssuePhotoPreview = preview;
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "tesla_issue_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        pendingCameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (pendingCameraUri == null) {
            Toast.makeText(this, "Could not create photo file", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_TAKE_ISSUE_PHOTO);
    }

    private void updateIssuePhotoPreview(ImageView preview, String uriText) {
        if (uriText == null || uriText.trim().isEmpty()) {
            preview.setImageDrawable(null);
            preview.setContentDescription("No issue photo selected");
            return;
        }
        try {
            preview.setImageURI(Uri.parse(uriText));
            preview.setContentDescription("Issue photo attached");
        } catch (Exception e) {
            preview.setImageDrawable(null);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || pendingIssuePhotoIndex < 0) return;

        Uri uri = null;
        if (requestCode == REQUEST_PICK_ISSUE_PHOTO && data != null) {
            uri = data.getData();
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) { }
            }
        } else if (requestCode == REQUEST_TAKE_ISSUE_PHOTO) {
            uri = pendingCameraUri;
        }

        if (uri != null) {
            prefs.edit().putString("photo_" + pendingIssuePhotoIndex, uri.toString()).apply();
            if (pendingIssuePhotoPreview != null) updateIssuePhotoPreview(pendingIssuePhotoPreview, uri.toString());
            Toast.makeText(this, "Issue photo attached", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveNote(int index, EditText notes) {
        prefs.edit().putString("notes_" + index, notes.getText().toString()).apply();
    }

    private void saveAllNotes() {
        android.content.SharedPreferences.Editor e = prefs.edit();
        for (ItemRow row : rows) e.putString("notes_" + row.index, row.notes.getText().toString());
        e.apply();
    }

    private void updateProgress() {
        int done = 0, issues = 0;
        for (int i=0; i<activeChecks.size(); i++) {
            int s = prefs.getInt("status_" + i, -1);
            if (s >= 0) done++;
            if (s == 1) issues++;
        }
        if (progressBar != null) progressBar.setProgress(done);
        if (progress != null) {
            String overall = done + " / " + activeChecks.size() + " checks complete" + (issues > 0 ? " • " + issues + " issue(s)" : "");
            if (openSection != null) overall += "\n" + openSection + ": " + sectionProgress(openSection);
            progress.setText(overall);
        }
    }

    private String buildReport() {
        saveAllNotes();
        StringBuilder sb = new StringBuilder();
        sb.append("Tesla ").append(selectedModel()).append(" Delivery Checklist Report\n");
        sb.append(new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK).format(new Date())).append("\n\n");
        appendOrderDetailsToReport(sb);
        String current = "";
        for (int i=0; i<activeChecks.size(); i++) {
            CheckItem item = activeChecks.get(i);
            if (!item.section.equals(current)) {
                current = item.section;
                sb.append("\n").append(current).append("\n");
            }
            int s = prefs.getInt("status_" + i, -1);
            String status = s == 0 ? "PASS" : s == 1 ? "ISSUE" : s == 2 ? "N/A" : "NOT CHECKED";
            String note = prefs.getString("notes_" + i, "").trim();
            sb.append("- [").append(status).append("] ").append(item.text);
            if (s == 1 && !note.isEmpty()) sb.append(" — ").append(note);
            if (s == 1) {
                String photo = prefs.getString("photo_" + i, "").trim();
                if (!photo.isEmpty()) sb.append(" — Photo attached: ").append(photo);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendOrderDetailsToReport(StringBuilder report) {
        appendOrderDetailsToReport(report, prefs);
    }

    private void appendOrderDetailsToReport(
        StringBuilder report,
        android.content.SharedPreferences orderPrefs
    ) {
        String orderNumber = orderPrefs.getString("order_number", "").trim();
        String vin = orderPrefs.getString("order_vin", "").trim();
        String eddStart = orderPrefs.getString("order_edd_start", "").trim();
        String eddEnd = orderPrefs.getString("order_edd_end", "").trim();
        String date = orderPrefs.getString("order_collection_date", "").trim();
        String time = orderPrefs.getString("order_collection_time", "").trim();
        String location = storedCollectionLocation(orderPrefs);
        if (orderNumber.isEmpty() && vin.isEmpty() && eddStart.isEmpty() && eddEnd.isEmpty()
            && date.isEmpty() && time.isEmpty() && location.isEmpty()) return;

        report.append("Order Details\n");
        if (!orderNumber.isEmpty()) report.append("Order: ").append(orderNumber).append("\n");
        if (!vin.isEmpty()) report.append("VIN: ").append(vin).append("\n");
        if (!eddStart.isEmpty() || !eddEnd.isEmpty()) {
            report.append("Estimated delivery: ");
            if (!eddStart.isEmpty()) report.append(eddStart);
            if (!eddEnd.isEmpty()) report.append(eddStart.isEmpty() ? "By " : " to ").append(eddEnd);
            report.append("\n");
        }
        if (!date.isEmpty()) report.append("Collection date: ").append(date).append("\n");
        if (!time.isEmpty()) report.append("Collection time: ").append(time).append("\n");
        if (!location.isEmpty()) report.append("Collection location: ").append(location).append("\n");
        report.append("\n");
    }

    private void shareReport() {
        String report = buildReport();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "TesSure Delivery Checklist Report");
        send.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(send, "Share checklist report"));
    }

    private String buildIssuesReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tesla ").append(selectedModel()).append(" Delivery Issues\n");
        sb.append(new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.UK).format(new Date())).append("\n\n");

        int total = totalIssueCount();
        sb.append(total).append(" issue").append(total == 1 ? "" : "s").append(" raised\n");

        ArrayList<String> sections = issueSections();
        for (String section : sections) {
            sb.append("\n").append(section).append("\n");
            int issueNumber = 0;
            for (int i=0; i<activeChecks.size(); i++) {
                if (prefs.getInt("status_" + i, -1) == 1 && activeChecks.get(i).section.equals(section)) {
                    issueNumber++;
                    String note = prefs.getString("notes_" + i, "").trim();
                    String photo = prefs.getString("photo_" + i, "").trim();
                    sb.append(issueNumber).append(". ").append(activeChecks.get(i).text).append("\n");
                    sb.append("   Issue: ").append(note.isEmpty() ? "No issue text added." : note).append("\n");
                    if (!photo.isEmpty()) sb.append("   Photo attached\n");
                }
            }
        }
        return sb.toString();
    }

    private ArrayList<Uri> issuePhotoUris() {
        ArrayList<Uri> photos = new ArrayList<>();
        for (int i=0; i<activeChecks.size(); i++) {
            if (prefs.getInt("status_" + i, -1) == 1) {
                String photo = prefs.getString("photo_" + i, "").trim();
                if (!photo.isEmpty()) photos.add(Uri.parse(photo));
            }
        }
        return photos;
    }

    private void shareIssuesReport() {
        if (totalIssueCount() == 0) {
            Toast.makeText(this, "No issues to export", Toast.LENGTH_SHORT).show();
            return;
        }

        String report = buildIssuesReport();
        ArrayList<Uri> photos = issuePhotoUris();
        Intent send;
        if (photos.isEmpty()) {
            send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
        } else {
            send = new Intent(Intent.ACTION_SEND_MULTIPLE);
            send.setType("image/*");
            send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, photos);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        send.putExtra(Intent.EXTRA_SUBJECT, "Tesla Delivery Issues");
        send.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(send, "Send issues to..."));
    }

    private void showIssues() {
        saveAllNotes();
        showIssueReviewPage(null);
    }

    private void showIssueReviewPage(String requestedSection) {
        rows.clear();
        progress = null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(42), dp(18), dp(14));
        header.setBackgroundColor(Color.rgb(14, 17, 25));
        root.addView(header);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        topRow.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1));

        TextView eyebrow = text("ISSUE REVIEW", 12, TESLA_RED, true);
        eyebrow.setLetterSpacing(0.12f);
        titleBlock.addView(eyebrow);

        TextView title = text("Work Through Issues", 25, TEXT, true);
        title.setPadding(0, dp(4), 0, dp(2));
        titleBlock.addView(title);

        Button hamburger = secondaryButton("☰");
        hamburger.setTextSize(24);
        hamburger.setOnClickListener(v -> showIssueReviewMenu(v));
        topRow.addView(hamburger, new LinearLayout.LayoutParams(dp(54), dp(48)));

        ArrayList<String> issueSections = issueSections();
        int totalIssues = totalIssueCount();
        TextView summary = text(totalIssues + " issue" + (totalIssues == 1 ? "" : "s") + " raised", 15, MUTED, false);
        header.addView(summary);

        if (!issueSections.isEmpty()) {
            TextView sectionLabel = text("Choose issue section", 13, MUTED, true);
            sectionLabel.setPadding(0, dp(12), 0, dp(4));
            header.addView(sectionLabel);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(92));
        scroll.addView(content);

        if (issueSections.isEmpty()) {
            TextView empty = text("No issues marked yet.", 17, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(36), dp(18), dp(36));
            content.addView(empty);
        } else {
            final String[] selectedSection = { requestedSection != null && issueSections.contains(requestedSection) ? requestedSection : issueSections.get(0) };

            Spinner issueSpinner = new Spinner(this);
            issueSpinner.setBackground(rounded(SURFACE_2, dp(12), BORDER, 1));
            issueSpinner.setPadding(dp(12), 0, dp(12), 0);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, issueSections) {
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    TextView view = (TextView) super.getView(position, convertView, parent);
                    styleIssueSectionText(view, issueSections.get(position), false);
                    return view;
                }

                @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                    styleIssueSectionText(view, issueSections.get(position), true);
                    return view;
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            issueSpinner.setAdapter(adapter);
            issueSpinner.setSelection(issueSections.indexOf(selectedSection[0]));
            issueSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedSection[0] = issueSections.get(position);
                    renderIssueSection(content, selectedSection[0]);
                }

                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
            header.addView(issueSpinner, new LinearLayout.LayoutParams(-1, dp(52)));
        }

        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void showIssueReviewMenu(View anchor) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(14), dp(12), dp(14), dp(12));
        menu.setBackground(rounded(Color.rgb(18, 22, 32), dp(18), BORDER, 1));

        TextView title = text("Issue review menu", 13, TESLA_RED, true);
        title.setLetterSpacing(0.08f);
        title.setPadding(dp(6), 0, dp(6), dp(8));
        menu.addView(title);

        final PopupWindow[] popup = new PopupWindow[1];
        addMenuItem(menu, "Export / Send Issues", "Share the issue review with Tesla staff", true, () -> {
            popup[0].dismiss();
            shareIssuesReport();
        });
        addMenuItem(menu, "Back to Checklist", "Return to the delivery checklist", false, () -> {
            popup[0].dismiss();
            showChecklistPage();
        });

        popup[0] = new PopupWindow(menu, dp(292), WindowManager.LayoutParams.WRAP_CONTENT, true);
        popup[0].setOutsideTouchable(true);
        popup[0].setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) popup[0].setElevation(dp(10));
        popup[0].setAnimationStyle(getResources().getIdentifier("ChecklistPopupAnimation", "style", getPackageName()));
        popup[0].showAsDropDown(anchor, -dp(238), dp(8));
    }

    private void styleIssueSectionText(TextView view, String section, boolean dropdown) {
        int issues = sectionIssueCount(section);
        view.setText("● " + issues + "  " + section);
        view.setTextColor(TEXT);
        view.setTextSize(16);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(dropdown ? 16 : 12), dropdown ? dp(14) : 0, dp(12), dropdown ? dp(14) : 0);
        if (dropdown) view.setBackgroundColor(SURFACE_2);
    }

    private ArrayList<String> issueSections() {
        ArrayList<String> sections = new ArrayList<>();
        for (int i=0; i<activeChecks.size(); i++) {
            if (prefs.getInt("status_" + i, -1) == 1 && !sections.contains(activeChecks.get(i).section)) {
                sections.add(activeChecks.get(i).section);
            }
        }
        return sections;
    }

    private int totalIssueCount() {
        int count = 0;
        for (int i=0; i<activeChecks.size(); i++) if (prefs.getInt("status_" + i, -1) == 1) count++;
        return count;
    }

    private void renderIssueSection(LinearLayout content, String section) {
        content.removeAllViews();
        TextView heading = text(section, 22, TEXT, true);
        heading.setPadding(dp(4), dp(4), dp(4), dp(4));
        content.addView(heading);

        TextView helper = text(sectionIssueCount(section) + " issue" + (sectionIssueCount(section) == 1 ? "" : "s") + " in this section. Review these with Tesla staff.", 14, MUTED, false);
        helper.setPadding(dp(4), 0, dp(4), dp(12));
        content.addView(helper);

        int issueNumber = 0;
        for (int i=0; i<activeChecks.size(); i++) {
            if (prefs.getInt("status_" + i, -1) == 1 && activeChecks.get(i).section.equals(section)) {
                issueNumber++;
                addIssueSummaryCard(content, i, issueNumber, section);
            }
        }
    }

    private void addIssueSummaryCard(LinearLayout parent, int index, int issueNumber, String returnSection) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(SURFACE_2, dp(14), BORDER, 1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, dp(12));
        parent.addView(card, cardParams);

        TextView heading = text("Issue " + issueNumber, 14, TESLA_RED, true);
        card.addView(heading);

        TextView itemText = text(activeChecks.get(index).text, 16, TEXT, true);
        itemText.setPadding(0, dp(6), 0, dp(8));
        card.addView(itemText);

        String note = prefs.getString("notes_" + index, "").trim();
        TextView noteText = text(note.isEmpty() ? "No issue text added." : note, 15, note.isEmpty() ? MUTED : TEXT, false);
        noteText.setPadding(0, 0, 0, dp(10));
        card.addView(noteText);

        String photo = prefs.getString("photo_" + index, "").trim();
        if (!photo.isEmpty()) {
            ImageView thumbnail = new ImageView(this);
            thumbnail.setImageURI(Uri.parse(photo));
            thumbnail.setAdjustViewBounds(true);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setBackground(rounded(SURFACE, dp(12), BORDER, 1));
            thumbnail.setContentDescription("Tap to view full-size issue photo");
            thumbnail.setOnClickListener(v -> showFullSizeIssuePhoto(photo, returnSection));
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, dp(170));
            card.addView(thumbnail, imageParams);

            TextView tapHint = text("Tap photo to view full size", 13, MUTED, false);
            tapHint.setPadding(0, dp(6), 0, 0);
            card.addView(tapHint);
        } else {
            TextView noPhoto = text("No photo attached.", 13, MUTED, false);
            card.addView(noPhoto);
        }
    }

    private void showFullSizeIssuePhoto(String photoUri, String returnSection) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(42), dp(18), dp(14));
        header.setBackgroundColor(Color.BLACK);
        root.addView(header);

        TextView title = text("Issue Photo", 24, TEXT, true);
        header.addView(title);

        Button back = secondaryButton("Back to Issues");
        back.setOnClickListener(v -> showIssueReviewPage(returnSection));
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-1, dp(48));
        backParams.setMargins(0, dp(10), 0, 0);
        header.addView(back, backParams);

        ImageView image = new ImageView(this);
        image.setImageURI(Uri.parse(photoUri));
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.BLACK);
        image.setPadding(dp(4), dp(4), dp(4), dp(4));
        root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
            .setTitle("Reset checklist?")
            .setMessage("This clears all ticks, notes, and issue photos for the selected " + selectedModel() + " checklist.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset", (d, w) -> {
                android.content.SharedPreferences.Editor editor = prefs.edit();
                for (int i = 0; i < activeChecks.size(); i++) {
                    editor.remove("status_" + i);
                    editor.remove("notes_" + i);
                    editor.remove("photo_" + i);
                }
                editor.apply();
                showChecklistPage();
            })
            .show();
    }

    @android.annotation.SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        if (showingOrderDetails) {
            leaveOrderDetails();
            return;
        }
        if (showingArchives) {
            leaveArchives();
            return;
        }
        CharSequence currentTitle = progress == null ? "" : progress.getText();
        if (currentTitle.length() > 0) {
            showLandingPage();
        } else {
            finish();
        }
    }
}
