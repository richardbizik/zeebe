package types

// This file was generated by the swagger tool.
// Editing this file might prove futile when you re-run the swagger generate command

// GraphDriverData Information about the storage driver used to store the container's and
// image's filesystem.
//
// swagger:model GraphDriverData
type GraphDriverData struct {

	// Low-level storage metadata, provided as key/value pairs.
	//
	// This information is driver-specific, and depends on the storage-driver
	// in use, and should be used for informational purposes only.
	//
	// Required: true
	Data map[string]string `json:"Data"`

	// Name of the storage driver.
	// Required: true
	Name string `json:"Name"`
}