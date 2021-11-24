# Add any ProGuard configurations specific to this
# extension here.

-keep public class xyz.kumaraswamy.tasks.Tasks {
    public *;
 }

 -keep public class xyz.kumaraswamy.tasks.ActivityService {
     public *;
  }

  -keep public class com.google.firebase.messaging.FirebaseMessagingService {
     public *;
  }

-keeppackagenames gnu.kawa**, gnu.expr**

-optimizationpasses 4
-allowaccessmodification
-mergeinterfacesaggressively

-repackageclasses 'xyz/kumaraswamy/tasks/repack'
-flattenpackagehierarchy
-dontpreverify

-dontwarn bsh.util.*, bsh.servlet.*