package org.freedesktop;
import java.util.List;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.UInt64;
import org.freedesktop.dbus.exceptions.DBusException;

public interface UDisks extends DBusInterface
{
   public static class PortChanged extends DBusSignal
   {
      public final DBusInterface a;
      public PortChanged(String path, DBusInterface a) throws DBusException
      {
         super(path, a);
         this.a = a;
      }
   }
   public static class PortRemoved extends DBusSignal
   {
      public final DBusInterface a;
      public PortRemoved(String path, DBusInterface a) throws DBusException
      {
         super(path, a);
         this.a = a;
      }
   }
   public static class PortAdded extends DBusSignal
   {
      public final DBusInterface a;
      public PortAdded(String path, DBusInterface a) throws DBusException
      {
         super(path, a);
         this.a = a;
      }
   }
   public static class ExpanderChanged extends DBusSignal
   {
      public final DBusInterface a;
      public ExpanderChanged(String path, DBusInterface a) throws DBusException
      {
         super(path, a);
         this.a = a;
      }
   }
   public static class ExpanderRemoved extends DBusSignal
   {
      public final DBusInterface a;
      public ExpanderRemoved(String path, DBusInterface a) throws DBusException
      {
         super(path, a);
         this.a = a;
      }
   }
   public static class ExpanderAdded extends DBusSignal
   {
      public final DBusInterface a;
      public ExpanderAdded(String path, DBusInterface a) throws DBusException
      {
         super(path, a);
         this.a = a;
      }
   }
   public static class AdapterChanged extends DBusSignal
   {
      public final DBusInterface a;
      public AdapterChanged(String path, DBusInterface a) throws DBusException
      {
         super(path, a);
         this.a = a;
      }
   }
   public static class AdapterRemoved extends DBusSignal
   {
      public final DBusInterface a;
      public AdapterRemoved(String path, DBusInterface a) throws DBusException
      {
         super(path, a);
         this.a = a;
      }
   }
   public static class AdapterAdded extends DBusSignal
   {
      public final DBusInterface a;
      public AdapterAdded(String path, DBusInterface a) throws DBusException
      {
         super(path, a);
         this.a = a;
      }
   }
   public static class DeviceJobChanged extends DBusSignal
   {
      public final DBusInterface a;
      public final boolean b;
      public final String c;
      public final UInt32 d;
      public final boolean e;
      public final double f;
      public DeviceJobChanged(String path, DBusInterface a, boolean b, String c, UInt32 d, boolean e, double f) throws DBusException
      {
         super(path, a, b, c, d, e, f);
         this.a = a;
         this.b = b;
         this.c = c;
         this.d = d;
         this.e = e;
         this.f = f;
      }
   }
   public static class DeviceChanged extends DBusSignal
   {
      public final DBusInterface a;
      public DeviceChanged(String path, DBusInterface a) throws DBusException
      {
         super(path, a);
         this.a = a;
      }
   }
   public static class DeviceRemoved extends DBusSignal
   {
      public final DBusInterface a;
      public DeviceRemoved(String path, DBusInterface a) throws DBusException
      {
         super(path, a);
         this.a = a;
      }
   }
   public static class DeviceAdded extends DBusSignal
   {
      public final DBusInterface a;
      public DeviceAdded(String path, DBusInterface a) throws DBusException
      {
         super(path, a);
         this.a = a;
      }
   }

  public void Uninhibit(String cookie);
  public String Inhibit();
  public DBusInterface LinuxMdCreate(List<DBusInterface> components, String level, UInt64 stripe_size, String name, List<String> options);
  public DBusInterface LinuxMdStart(List<DBusInterface> components, List<String> options);
  public DBusInterface LinuxLvm2LVCreate(String group_uuid, String name, UInt64 size, UInt32 num_stripes, UInt64 stripe_size, UInt32 num_mirrors, List<String> options, String fstype, List<String> fsoptions);
  public void LinuxLvm2LVRemove(String group_uuid, String uuid, List<String> options);
  public void LinuxLvm2LVStart(String group_uuid, String uuid, List<String> options);
  public void LinuxLvm2LVSetName(String group_uuid, String uuid, String name);
  public void LinuxLvm2VGRemovePV(String vg_uuid, String pv_uuid, List<String> options);
  public void LinuxLvm2VGAddPV(String uuid, DBusInterface physical_volume, List<String> options);
  public void LinuxLvm2VGSetName(String uuid, String name);
  public void LinuxLvm2VGStop(String uuid, List<String> options);
  public void LinuxLvm2VGStart(String uuid, List<String> options);
  public void DriveUnsetAllSpindownTimeouts(String cookie);
  public String DriveSetAllSpindownTimeouts(int timeout_seconds, List<String> options);
  public void DriveUninhibitAllPolling(String cookie);
  public String DriveInhibitAllPolling(List<String> options);
  public DBusInterface FindDeviceByMajorMinor(long device_major, long device_minor);
  public DBusInterface FindDeviceByDeviceFile(String device_file);
  public List<String> EnumerateDeviceFiles();
  public List<DBusInterface> EnumerateDevices();
  public List<DBusInterface> EnumeratePorts();
  public List<DBusInterface> EnumerateExpanders();
  public List<DBusInterface> EnumerateAdapters();

    public interface Device extends DBusInterface
    {
        public static class JobChanged extends DBusSignal
        {
            public final boolean a;
            public final String b;
            public final UInt32 c;
            public final boolean d;
            public final double e;
            public JobChanged(String path, boolean a, String b, UInt32 c, boolean d, double e) throws DBusException
            {
                super(path, a, b, c, d, e);
                this.a = a;
                this.b = b;
                this.c = c;
                this.d = d;
                this.e = e;
            }
        }
        public static class Changed extends DBusSignal
        {
            public Changed(String path) throws DBusException
            {
                super(path);
            }
        }

        public Triplet<List<Struct1>, List<Struct2>, List<Struct3>> DriveBenchmark(boolean do_write_benchmark, List<String> options);
        public void DriveAtaSmartInitiateSelftest(String test, List<String> options);
        public void DriveAtaSmartRefreshData(List<String> options);
        public void DriveUnsetSpindownTimeout(String cookie);
        public String DriveSetSpindownTimeout(int timeout_seconds, List<String> options);
        public void DriveDetach(List<String> options);
        public void DriveEject(List<String> options);
        public void DrivePollMedia();
        public void DriveUninhibitPolling(String cookie);
        public String DriveInhibitPolling(List<String> options);
        public UInt64 LinuxMdCheck(List<String> options);
        public void LinuxLvm2LVStop(List<String> options);
        public void LinuxMdStop(List<String> options);
        public void LinuxMdRemoveComponent(DBusInterface component, List<String> options);
        public void LinuxMdExpand(List<DBusInterface> components, List<String> options);
        public void LinuxMdAddSpare(DBusInterface component, List<String> options);
        public void LuksChangePassphrase(String current_passphrase, String new_passphrase);
        public void LuksLock(List<String> options);
        public DBusInterface LuksUnlock(String passphrase, List<String> options);
        public List<Struct4> FilesystemListOpenFiles();
        public boolean FilesystemCheck(List<String> options);
        public void FilesystemUnmount(List<String> options);
        public String FilesystemMount(String filesystem_type, List<String> options);
        public void FilesystemSetLabel(String new_label);
        public void FilesystemCreate(String fstype, List<String> options);
        public void PartitionModify(String type, String label, List<String> flags);
        public DBusInterface PartitionCreate(UInt64 offset, UInt64 size, String type, String label, List<String> flags, List<String> options, String fstype, List<String> fsoptions);
        public void PartitionDelete(List<String> options);
        public void PartitionTableCreate(String scheme, List<String> options);
        public void JobCancel();

    }

}
