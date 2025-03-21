package pt.ulisboa.tecnico.cnv.loadbalancer;

public class RequestParameters {
    // Raytracer Parameters
    public boolean aa = false;
    public boolean multi = false;
    public int scols = 0;
    public int srows = 0;
    public int wcols = 0;
    public int wrows = 0;
    public int coff = 0;
    public int roff = 0;
    public long imageSize = 0;
    public long texmapSize = 0;
    public byte[] input = null;
    public byte[] texmap = null;

    // ImageProc Parameters
    public String imageFormat = "";
    public String image = "";

    public RequestParameters() {
    }

    @Override
    public String toString() {
        return "RequestParameters{" + "aa=" + aa + ", multi=" + multi + ", scols=" + scols + ", srows=" + srows + ", wcols=" + wcols + ", wrows=" + wrows + ", coff=" + coff + ", roff=" + roff + ", imageSize=" + imageSize + ", texmapSize=" + texmapSize + ", imageFormat=" + imageFormat + '}';
    }
}
